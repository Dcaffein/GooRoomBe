# PLAN — 플래그 종료 이벤트 정리 (task-103) 구현 방법

브랜치: `ai/fix-flag-conclusion-events` (main에서 분기)

---

## 커밋 1 — 삭제 알림 대상

`FlagDeletionEventListener:48`의 비교 대상 하나만 바꾼다.

```java
- if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.RECRUITING) {
+ if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.ENDED) {
```

`publishNotification`의 문구, `NotificationType`, 수신자 조회는 손대지 않는다.

**테스트:** `FlagDeletionEventListenerTest`에 네 상태 케이스를 추가한다. 이 클래스는
현재 알림 발행을 한 건도 검증하지 않아서 새로 짜는 것에 가깝다. `eventPublisher`가
여러 타입을 받으므로 `verify(..., never())`에 인자 타입을 명시한다.

```java
verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
```

## 커밋 2 — 일정 변경 이벤트 유실

`FlagModificationService.reschedule:38-41`에 `save()`를 넣는다.

```java
 public void reschedule(FlagScheduleUpdateCommand command) {
     FlagSchedule newSchedule = FlagSchedule.of(...);
-    getFlagOrThrow(command.flagId()).reschedule(command.hostId(), newSchedule);
+    Flag flag = getFlagOrThrow(command.flagId());
+    flag.reschedule(command.hostId(), newSchedule);
+    flagRepository.save(flag);
 }
```

Spring Data의 `@DomainEvents`는 리포지토리의 `save`/`delete` 호출에서만 발행된다.
더티 체킹 플러시로는 발행되지 않아 `Flag.reschedule`이 등록한 `FlagMeetingChangedEvent`가
버려지고 있었다. 같은 파일 `closeFlag:54`는 `save()`를 부르고 있어서 삭제 알림만 동작했다.

**같은 함정을 밟은 곳이 더 있는지 먼저 확인한다.** `registerEvent` 호출부와 `save()`
호출 여부를 대조했고 현재 등록 지점은 셋이다.

| 등록 지점 | 호출부 | `save()` |
|---|---|---|
| `Flag.delete` | `FlagModificationService.closeFlag` | 있음 |
| `Flag.reschedule` | `FlagModificationService.reschedule` | **없음 (이 커밋)** |
| `Flag.createEncore` | `FlagHostService.encoreFlag` | 있음 (반환값을 쓴다) |

**테스트:** 단위 테스트로는 `@DomainEvents` 발행 여부를 잡을 수 없다. 통합 테스트에서
일정 변경 후 `FlagMeetingChangedEventListener`가 실제로 깨어나는지 확인한다.
`FlagModificationServiceTest`에는 시간이 그대로면 발행되지 않는 케이스를 넣는다
(`Flag.isMeetingTimeChanged`가 이미 거른다).

## 커밋 3 — 친밀도 발행을 한 곳으로 모은다

### 3-1. 이벤트와 리스너 신설

```java
// flag/domain/flag/event/FlagConcludedEvent.java
public record FlagConcludedEvent(Long flagId, Long hostId, Long parentId) {}
```

```java
// flag/application/eventListener/FlagConclusionEventListener.java
@Component
@RequiredArgsConstructor
public class FlagConclusionEventListener {

    private final FlagRepository flagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(FlagConcludedEvent event) {
        List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());
        if (participantIds.isEmpty()) return;

        InteractionType type = event.parentId() != null
                ? InteractionType.FLAG_ENDED_ENCORE : InteractionType.FLAG_ENDED;
        eventPublisher.publishEvent(
                new BatchMutualInteractionEvent(participantIds, event.hostId(), type));
    }
}
```

애노테이션 구성은 기존 `FlagDeletionEventListener`와 동일하게 맞춘다. 소프트 삭제된
플래그여도 `flag_participants`에는 `@SQLRestriction`이 없어 조회된다. 퍼지는 소프트
삭제 12시간 뒤 배치라 리스너 실행 시점에는 행이 남아 있다.

### 3-2. 삭제 리스너에서 친밀도 로직을 걷어낸다

`FlagDeletionEventListener`에서 `isMeetingHeld`, `publishInteractionEvents`,
`InteractionType`·`BatchMutualInteractionEvent` import를 제거하고 발행으로 바꾼다.

```java
private void notifyParticipants(FlagDeletedEvent event, Long hostId) {
    List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());
    if (participantIds.isEmpty()) return;

    if (event.statusAtDeletion() != FlagStatus.ENDED) {
        publishNotification(participantIds, event.flagTitle());
    } else {
        eventPublisher.publishEvent(
                new FlagConcludedEvent(event.flagId(), hostId, event.parentId()));
    }
}
```

`isEmpty()` 검사를 앞으로 빼서 두 분기에서 반복하던 것을 없앤다. `if/else`로 두면
두 분기가 배타적이라는 사실이 구조에 드러난다. **기존 동작에서 `IN_ACTIVITY`가
친밀도 대상에서 빠지는 지점이 여기다.**

`FlagConcludedEvent`는 이 리스너의 `REQUIRES_NEW` 트랜잭션 안에서 발행되므로
`AFTER_COMMIT` 리스너가 정상적으로 받는다. 지금 `BatchMutualInteractionEvent`가
같은 방식으로 동작하고 있다.

**테스트:** `FlagConclusionEventListenerTest`를 새로 만든다. `parentId` 유무로
타입이 갈리는지, 참여자가 비면 발행하지 않는지.

## 커밋 4 — 나머지 두 경로

### 4-1. 만료 스윕

현재 벌크 UPDATE 하나를 **대상 조회 → UPDATE → 발행** 세 단계로 나눈다.

**조회 조건은 바뀌지 않는다.** 지금 UPDATE의 WHERE와 같은 집합이다. 조회를 따로 두는
이유는 두 가지다.

- 벌크 UPDATE는 **건수만 돌려준다.** 플래그마다 이벤트를 발행하려면 id가 필요하다
- 이벤트가 `hostId`와 `parentId`를 **직접 실어야 한다.** 리스너가 도는 시점에 그
  플래그는 이미 소프트 삭제된 상태이고, `findById`는 `@SQLRestriction` 때문에
  빈 결과를 준다. 나중에 되읽을 수 없어서 발행 시점에 담아 보낸다.
  기존 `FlagDeletedEvent`가 `hostId`·`parentId`를 들고 다니는 것도 같은 이유다

엔티티를 통째로 로드하지 않고 프로젝션으로 세 필드만 가져온다.

```java
interface FlagExpiryTarget {
    Long getId();
    Long getHostId();
    Long getParentId();
}

@Query("SELECT f.id AS id, f.hostId AS hostId, f.parentId AS parentId FROM Flag f " +
       "WHERE f.schedule.endDateTime < :threshold AND f.autoExpiryExempt = false")
List<FlagExpiryTarget> findExpiryTargets(@Param("threshold") LocalDateTime threshold,
                                         Pageable pageable);
```

`@SQLRestriction`이 `deleted_at IS NULL`을 자동으로 붙이므로 명시할 조건은 둘뿐이다.
현재 UPDATE의 WHERE와 같은 집합이 나온다. `V3__add_flag_indexes.sql`의
`idx_flags_end_date_time`을 그대로 탄다.

`expireAllExceedingThreshold`는 id 목록을 받는 형태로 바꾼다.

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Flag f SET f.deletedAt = :now WHERE f.id IN :ids AND f.deletedAt IS NULL")
int expireByIds(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);
```

서비스는 이렇게 된다.

```java
@Transactional
public void expireEndedFlags() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime threshold = now.minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

    // 초대 정리가 먼저다. 소프트 삭제를 먼저 하면 Flag의 @SQLRestriction 때문에
    // 방금 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
    int purgedInvitations = maintenancePort.purgeInvitationsOfEndedFlags(threshold);

    List<FlagExpiryTarget> targets =
            flagRepository.findExpiryTargets(threshold, PageRequest.of(0, BATCH_SIZE));
    int expiredFlags = 0;

    if (!targets.isEmpty()) {
        expiredFlags = flagRepository.expireByIds(
                targets.stream().map(FlagExpiryTarget::getId).toList(), now);
        targets.forEach(t -> eventPublisher.publishEvent(
                new FlagConcludedEvent(t.getId(), t.getHostId(), t.getParentId())));
    }

    if (expiredFlags > 0 || purgedInvitations > 0) { ... }   // 기존 로그 유지
}
```

**초대 정리를 먼저 하는 순서는 유지한다.** 기존 주석도 그대로 옮긴다.

`BATCH_SIZE = 5000`을 새로 둔다. 지금 UPDATE에는 상한이 없어 밀린 물량이 많으면
이벤트가 한 번에 쏟아진다. `FlagPurgeService`와 같은 값으로 맞추고, 남은 것은
6시간 뒤 다음 회차가 가져간다.

### 4-2. 보존 활성화

**`registerEvent`를 쓰지 않는다.** `FlagExpiryExemptionPolicy.refresh()`는
`findById` 후 더티 체킹으로만 값을 바꾸고 `save()`를 부르지 않으므로 커밋 2와 같은
이유로 이벤트가 버려진다. `ApplicationEventPublisher`로 직접 발행한다.

```java
public void refresh(Long flagId) {
    Flag flag = flagRepository.findById(flagId)
            .orElseThrow(() -> new FlagNotFoundException(flagId));

    boolean wasExempt = flag.isAutoExpiryExempt();
    boolean exempt = memorialRepository.existsByFlagId(flagId)
                  || flagRepository.existsByParentId(flagId);
    flag.updateAutoExpiryExempt(exempt);

    if (exempt && !wasExempt) {
        eventPublisher.publishEvent(
                new FlagConcludedEvent(flag.getId(), flag.getHostId(), flag.getParentId()));
    }
}
```

전이 검출을 정책 안에 두는 이유는 호출부가 넷인데 전이를 아는 것은 정책뿐이기
때문이다. 호출부에서 판단하게 하면 같은 3줄이 여러 군데 복제된다.

`refresh()` 호출부는 이렇다.

| 호출부 | 트랜잭션 단계 | `false → true` 가능성 |
|---|---|---|
| `FlagMemorialEventListener.handleMemorialCreated` | `BEFORE_COMMIT` | **있다 (첫 후기)** |
| `FlagEncoreEventListener.handleEncoreCreated` | `BEFORE_COMMIT` | **있다 (첫 앵콜)** |
| `FlagMemorialEventListener.handleMemorialDeleted` | `BEFORE_COMMIT` | 없다 (해제 방향) |
| `FlagDeletionEventListener:42` | `AFTER_COMMIT` + `REQUIRES_NEW` | 없다 (자식 삭제 후 부모 재계산, 해제 방향) |

**확인이 필요한 전제는 위쪽 둘이다.** `BEFORE_COMMIT` 단계에서 발행한 이벤트도
트랜잭션 동기화에 등록되어 `AFTER_COMMIT` 리스너가 받는 것이 맞지만, 단위 테스트로는
검증되지 않는다. **통합 테스트로 실제 확인한다.**

세 경로 전체로 보면 발행 지점의 단계가 서로 다르다. ①은 `FlagDeletionEventListener`의
`REQUIRES_NEW` 트랜잭션 안(지금 `BatchMutualInteractionEvent`가 발행되는 바로 그 자리),
②는 `FlagExpiryService`의 일반 `@Transactional` 안, ③만 `BEFORE_COMMIT`이다.
**①과 ②는 이미 동작이 확인된 형태라 새로 확인할 것이 없다.**

**테스트:** `FlagExpiryServiceTest`에 소프트 삭제 건수만큼 발행되는지, 보존 플래그가
대상에서 빠지는지, 배치 상한이 걸리는지, 초대 정리가 먼저인지를 넣는다.
통합 테스트에서는 첫 후기 작성에 발행되고 두 번째 후기 작성에는 발행되지 않는 것을 본다.

---

## 확인해둔 사실

구현 중 전제가 흔들리면 멈추고 보고할 지점이다.

- `BatchMutualInteractionEvent` 발행 지점은 현재 `FlagDeletionEventListener:66`
  **한 곳뿐**이다. 커밋 3 이후 `FlagConclusionEventListener`가 유일한 발행 지점이 된다
- 보존 원천 둘이 모두 종료 이후에만 발생한다. `FlagMemorialFactory:16`의
  `if(!flag.isEnded()) throw`, `Flag.createEncore:84`의 `if (!this.isEnded()) throw`.
  **이 가드가 없으면 4-2가 열리지 않은 모임에 친밀도를 준다**
- `flag_participants`에는 `@SQLRestriction`이 없다. 소프트 삭제 후에도 조회된다
- social 쪽은 한 줄도 바뀌지 않는다. `FriendInteractionEventListener`가 받는 이벤트
  타입과 페이로드가 그대로다
- 스키마 변경 없음. 마이그레이션 파일을 추가하지 않는다
