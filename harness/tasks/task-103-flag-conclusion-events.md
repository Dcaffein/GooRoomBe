# Task-103: 플래그 종료 이벤트 정리

> **Domain Change:** [x] — `FlagExpiryExemptionPolicy`가 전이 여부를 판별해 이벤트를 발행한다.
> 필드는 추가하지 않는다. 스키마 변경 없음, 마이그레이션 없음.

> **FE 협조 필요:** [ ] — 응답 스키마도 알림 타입도 그대로다. 다만 사용자에게 보이는
> 변화가 둘 있다. 모집 중 취소에 알림이 새로 가고(1절), **지금까지 한 번도 나가지 않던
> 일정 변경 알림이 나가기 시작한다**(3-3).

> **선행 조건:** 커밋 4개를 같은 브랜치에서 순서대로 쌓는다.

## Background

호스트가 플래그를 삭제하면 `Flag.delete()`(`Flag.java:123`)가 삭제 시점의
`calculateCurrentStatus()`를 `FlagDeletedEvent.statusAtDeletion`에 담아 발행하고,
`FlagDeletionEventListener`가 이 값 하나로 **두 가지**를 결정한다.

```java
if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.RECRUITING) {
    publishNotification(participantIds, event.flagTitle());          // 취소 알림
}
if (!participantIds.isEmpty() && isMeetingHeld(event.statusAtDeletion())) {
    publishInteractionEvents(participantIds, hostId, ...);           // 친밀도
}
```

두 분기가 다 어긋나 있고, 원인이 서로 다르다.

### 문제 1 — 알림이 정반대로 나간다

| 삭제 시점 | 알림 | 적절성 |
|---|---|---|
| `RECRUITING` | **안 감** | 이미 참여한 사람이 있어도 일정이 조용히 사라진다 |
| `WAITING` | 감 | 맞다 |
| `IN_ACTIVITY` | 감 | 맞다 |
| `ENDED` | 감 | **틀리다.** 취소할 일정이 없는데 "취소되었습니다"가 나간다 |

`delete()`에는 `validateNotEnded()`가 없어 종료 후 삭제가 허용된다.

### 문제 2 — 친밀도가 호스트의 정리 습관에 좌우된다

`BatchMutualInteractionEvent`(`FLAG_ENDED`)를 발행하는 곳은 **전 코드베이스에
`FlagDeletionEventListener:66` 한 곳뿐**이다. 그 리스너는 `FlagDeletedEvent`로만
깨어나고, 그 이벤트는 `Flag.delete()`에서만 등록된다.

자동 만료는 이 경로를 타지 않는다. `expireAllExceedingThreshold`가
`@Modifying` 벌크 UPDATE라 엔티티를 로드하지 않고, 따라서 `registerEvent`도
`@DomainEvents`도 타지 않는다.

결과적으로 친밀도가 오르는 경로는 이렇다.

| 경로 | 친밀도 |
|---|---|
| 모임 종료 후 아무도 안 지움 → 24시간 뒤 자동 만료 | **0** |
| 후기·앵콜이 달림 → `autoExpiryExempt = true` → **스윕에서 제외** | **0** (영구) |
| 호스트가 `IN_ACTIVITY` / `ENDED`에서 수동 삭제 | 지급 |

두 번째 줄이 역전이다. `FlagExpiryExemptionPolicy`는 후기나 앵콜이 달린 플래그를
**보존하려고** 스윕에서 빼는데, 그 결과 가장 활발했던 모임이 친밀도를 영영 못 받는다.

**근본 원인은 트리거가 "모임이 끝났다"가 아니라 "플래그가 삭제됐다"에 걸린 것이다.**
`FlagStatus.ENDED`는 `endDateTime`과 현재 시각을 비교해 조회 때마다 계산되는
파생값이라 전이 순간이 없고, 그래서 가장 가까운 관측 지점이었던 삭제에 얹혔다.

---

## 1. 알림 기준을 "종료되었는가"로 뒤집는다

이 알림의 목적은 **일정 취소를 알리는 것**이다. 기준은 "앞으로의(또는 진행 중인)
약속이 사라졌는가"여야 하고, 모집 단계인지는 무관하다.

```java
// FlagDeletionEventListener:48
- if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.RECRUITING) {
+ if (!participantIds.isEmpty() && event.statusAtDeletion() != FlagStatus.ENDED) {
```

문구와 `NotificationType.FLAG_CANCELED`는 **그대로 둔다.** 대상이 종료 전 세 상태로
좁혀지면 `"[%s] 모임이 호스트 사정으로 취소되었습니다."`가 세 경우 모두에 맞는다.

`ENDED` 삭제가 무음이 되면서 참여자의 후기·댓글이 퍼지 배치에 함께 지워지는 것도
통지되지 않는다. **의도된 선택이다.** 이 채널은 일정 취소 통지이고, 종료된 모임의
기록 삭제는 다른 종류의 사건이다. 필요해지면 새 알림 타입으로 내고 `FLAG_CANCELED`에
얹지 않는다.

## 2. 친밀도 발행을 `FlagConcludedEvent` 하나로 모은다

"이 모임이 실제로 열렸고 끝났다"는 사실을 flag가 발행하고, 친밀도 환산은 social이
한다. flag는 몇 점짜리인지 모른다.

```java
// flag/domain/flag/event/FlagConcludedEvent
public record FlagConcludedEvent(Long flagId, Long hostId, Long parentId) {}
```

```java
// flag/application/eventListener/FlagConclusionEventListener
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handle(FlagConcludedEvent event) {
    List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());
    if (participantIds.isEmpty()) return;

    InteractionType type = event.parentId() != null
            ? InteractionType.FLAG_ENDED_ENCORE : InteractionType.FLAG_ENDED;
    eventPublisher.publishEvent(new BatchMutualInteractionEvent(participantIds, event.hostId(), type));
}
```

참여자 조회 후 발행하는 형태는 지금 `FlagDeletionEventListener`가 하는 것과 같다.
소프트 삭제된 플래그여도 `flag_participants`에는 `@SQLRestriction`이 없어 조회된다.
퍼지는 12시간 뒤에 도니 리스너 실행 시점에는 행이 남아 있다.

`FlagDeletionEventListener`에서 `publishInteractionEvents` / `isMeetingHeld` /
`InteractionType` import를 **전부 제거한다.** 그 리스너는 알림과 부모 링크 절단만
담당하게 된다.

### 삭제 리스너는 상보적인 두 분기가 된다

```java
if (statusAtDeletion != ENDED)  → 취소 알림
if (statusAtDeletion == ENDED)  → FlagConcludedEvent 발행
```

기존 `isMeetingHeld`에서 **`IN_ACTIVITY`가 빠진다.** 진행 중 취소에 "취소되었습니다"
알림과 친밀도를 동시에 주는 것이 모순이었다. 같은 값에 대한 배타적 두 분기가 된다.

## 3. 나머지 두 경로에서도 발행한다

발행 지점 셋 모두 **이미 존재하는 1회성 전이**에 얹는다. 새 컬럼도 새 스케줄러도 없다.

| 지점 | 1회성 근거 |
|---|---|
| 호스트가 `ENDED`에서 삭제 | `Flag.delete()`의 `isDeleted()` 가드 (2절) |
| 만료 스윕이 소프트 삭제 | `deleted_at IS NULL` 가드 |
| 면제가 `false → true` | 엔티티가 현재 값을 안다 |

### 3-1. 만료 스윕

`FlagExpiryService.expireEndedFlags()`의 벌크 UPDATE를 **대상 조회 → UPDATE → 발행**으로
바꾼다. 참여자 조회를 위해 어차피 id가 필요하다.

```java
int purgedInvitations = maintenancePort.purgeInvitationsOfEndedFlags(threshold);  // 순서 유지
List<FlagExpiryTarget> targets = flagRepository.findExpiryTargets(threshold, BATCH_SIZE);
int expiredFlags = flagRepository.expireByIds(targetIds, now);
targets.forEach(t -> eventPublisher.publishEvent(
        new FlagConcludedEvent(t.id(), t.hostId(), t.parentId())));
```

**초대장 정리를 먼저 하는 순서는 반드시 유지한다.** `Flag`의
`@SQLRestriction("deleted_at IS NULL")`이 초대 삭제 쿼리의 서브쿼리에도 적용돼서,
소프트 삭제를 먼저 하면 방금 지운 플래그가 서브쿼리에서 빠진다. 기존 주석 참조.

조회 조건은 현재 UPDATE의 WHERE와 같다. `@SQLRestriction`이 `deleted_at IS NULL`을
자동으로 붙이므로 명시할 것은 둘이다.

```java
@Query("SELECT f.id AS id, f.hostId AS hostId, f.parentId AS parentId FROM Flag f " +
       "WHERE f.schedule.endDateTime < :threshold AND f.autoExpiryExempt = false")
```

`V3__add_flag_indexes.sql`의 `idx_flags_end_date_time`을 그대로 쓴다. 인덱스 추가 없음.

**배치 상한을 둔다.** 지금 UPDATE에는 상한이 없어 밀린 물량이 많으면 이벤트가 한 번에
쏟아진다. 퍼지와 같은 5000으로 맞춘다. 남은 것은 6시간 뒤 다음 회차가 가져간다.

### 3-2. 면제 획득

**`registerEvent`를 쓰면 안 된다.** `FlagExpiryExemptionPolicy.refresh()`는
`findById` 후 더티 체킹으로만 값을 바꾸고 `save()`를 호출하지 않는다. Spring Data의
`@DomainEvents`는 리포지토리의 `save`/`delete` 호출에서만 발행되므로 등록된 이벤트가
그대로 버려진다. `ApplicationEventPublisher`로 직접 발행한다.

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

**이 경로가 안전한 이유는 면제 원천 둘이 모두 종료 이후에만 발생하기 때문이다.**
`FlagMemorialFactory:16`이 `if(!flag.isEnded()) throw`, `Flag.createEncore:84`가
`if (!this.isEnded()) throw`로 막고 있다. 열리지도 않은 모임에 친밀도가 나갈 수 없다.
**이 두 가드를 제거하면 이 설계가 깨진다.**

`refresh()`를 호출하는 세 곳(`FlagMemorialEventListener` 생성·삭제,
`FlagEncoreEventListener`)은 모두 `BEFORE_COMMIT` 리스너다. 이 단계에서 발행한
이벤트도 트랜잭션 동기화에 등록되어 `AFTER_COMMIT` 리스너가 정상적으로 받는다.
**통합 테스트로 실제 확인한다.** 검증 방법 절 참조.

### 3-3. 같은 원인의 유실 하나를 함께 고친다

3-2에서 확인한 `@DomainEvents` 발행 조건 때문에 **일정 변경 알림이 나가지 않고 있다.**

`Flag.reschedule()`(`Flag.java:168`)은 시간이 바뀌면 `FlagMeetingChangedEvent`를
등록하는데, 호출부가 `save()`를 부르지 않는다.

```java
// FlagModificationService:38-41
public void reschedule(FlagScheduleUpdateCommand command) {
    FlagSchedule newSchedule = FlagSchedule.of(...);
    getFlagOrThrow(command.flagId()).reschedule(command.hostId(), newSchedule);   // save 없음
}
```

같은 파일의 `closeFlag:54`는 `flagRepository.save(flag)`를 명시적으로 호출한다.
그 차이 때문에 삭제 알림은 나가고 일정 변경 알림은 나가지 않는다.
`FlagMeetingChangedEventListener`는 현재 죽은 코드다.

```java
+ flagRepository.save(flag);
```

**한 줄이다.** 이 task에 넣는 이유는 원인이 3-2와 같아서다. "더티 체킹만으로는
도메인 이벤트가 발행되지 않는다"를 두 곳에서 각각 발견하고 각각 고치면, 다음에
`registerEvent`를 쓰는 사람이 같은 함정을 다시 밟는다. 한 커밋에 묶어 근거를 한 번에 남긴다.

**같은 함정을 밟은 곳이 더 있는지 확인한다.** `registerEvent` 호출부와
`save()` 호출 여부를 대조한다. 현재 등록 지점은 셋이다.

| 등록 지점 | 호출부 | `save()` |
|---|---|---|
| `Flag.delete` → `FlagDeletedEvent` | `FlagModificationService.closeFlag` | 있음 |
| `Flag.reschedule` → `FlagMeetingChangedEvent` | `FlagModificationService.reschedule` | **없음** |
| `Flag.createEncore` → `FlagEncoreEvent` | `FlagHostService.encoreFlag` | 있음 (`save`의 반환값을 쓴다) |

## 4. 중복 지급을 감수한다

정상 경로에서는 셋이 이미 배타적이다. 면제 플래그는 스윕이 건너뛰고, 소프트 삭제된
플래그는 `@SQLRestriction` 때문에 면제가 붙을 수 없으며, 삭제와 스윕은 같은
`deleted_at`을 두고 경쟁한다. ①과 ②는 각각 `isDeleted()`와 `deleted_at IS NULL`
가드로 스스로도 1회다.

**중복이 생기는 원인은 하나뿐이다. `autoExpiryExempt`가 `true → false`로 되돌아가는 것.**
③이 이미 지급한 뒤 면제가 꺼지면 그 플래그는 다시 세 경로 전부에 열린다.

```
모임 종료 → 후기 작성(면제 on, 지급) → 후기 삭제(면제 off) → 다음 중 아무거나 (재지급)
                                                              ├ 후기 재작성 → ③
                                                              ├ 만료 스윕    → ②
                                                              └ 호스트 삭제  → ①
```

앵콜도 같다. 앵콜을 만들었다가 지우면 부모의 면제가 꺼진다
(`FlagDeletionEventListener`가 부모에 `refresh`를 건다).

**면제를 한 번도 끄지 않은 플래그는 정확히 한 번 지급된다.** 이 한 갈래를 막으려고
마커 컬럼이나 정산 원장을 만들지 않는다. 근거는 점수의 성격이다.

- `FriendshipDecayPolicy`: 30일 유예 후 **매일 3.3% 감쇄**. 21.4점이 90일이면 1.0점
- `InteractionScorePolicy`: `VISIT`은 방문마다 1.0점씩 **중복 관리가 아예 없다.**
  `BUZZ_SEND`도 보낼 때마다 10.0점

이 시스템 어디에도 "정확히 한 번"은 없다. 감쇄하는 휴리스틱 점수이고 네트워크 시각화
정렬에 쓰인다. 잔고가 아니다. `FLAG_ENDED` 10점이 두 번 들어가는 오차는
`VISIT`의 무제한 누적보다 작다.

**대신 지불하지 않는 대가가 있다.** 배타성을 flag 쪽에서 보장하려면 면제를 단방향
래치로 바꿔야 하는데, 그러면 "마지막 후기를 지우면 다시 만료 대상"이라는 보존 정책을
앞으로 되돌릴 수 없게 된다. 보존 정책이 친밀도 정산에 묶이는 것이라 받지 않는다.

---

## 커밋 분할

```
fix(flag): 삭제 알림 대상을 종료 전 플래그의 참여자로 바로잡는다
fix(flag): save 누락으로 유실되던 일정 변경 이벤트를 발행한다
refactor(flag): 종료 친밀도 발행을 FlagConcludedEvent로 모은다
fix(flag): 자동 만료와 면제 획득 경로에서도 종료 친밀도를 발행한다
```

2번을 3·4번보다 앞에 둔다. 독립적으로 되돌릴 수 있어야 하고, 3-2가 왜
`ApplicationEventPublisher`를 쓰는지에 대한 근거가 이 커밋 메시지에 남는다.

1번이 FE에 보이는 유일한 변화다. 순서를 바꾸지 않는다. 각 커밋은 자체 테스트를 포함한다.

## 검증 방법

`FlagDeletionEventListenerTest`는 현재 **알림도 친밀도도 한 건도 검증하지 않는다.**
부모 링크 절단과 보존 상태 재계산만 본다.

- `FlagDeletionEventListenerTest` — 네 상태 전부
  - `RECRUITING`/`WAITING`/`IN_ACTIVITY` → `NotificationEvent` 발행,
    `receiverIds`가 참여자 전원, `type`이 `FLAG_CANCELED`
  - `ENDED` → `NotificationEvent` **미발행**, `FlagConcludedEvent` 발행
  - `IN_ACTIVITY` → `FlagConcludedEvent` **미발행** (기존 동작에서 빠지는 지점)
  - 참여자가 비면 어떤 상태에서도 알림 미발행
  - `eventPublisher`가 여러 타입을 받으므로 `never()` 검증 시 인자 타입을 좁힌다
- `FlagConclusionEventListenerTest` — `parentId`가 있으면 `FLAG_ENDED_ENCORE`,
  없으면 `FLAG_ENDED`. 참여자 없으면 미발행
- `FlagExpiryServiceTest` — 소프트 삭제된 건수만큼 `FlagConcludedEvent`가 나오는지.
  **면제 플래그는 대상에서 빠지는지.** 배치 상한이 걸리는지.
  초대장 정리가 소프트 삭제보다 먼저인지
- `FlagModificationServiceTest` — `reschedule`이 시간을 바꾸면
  `FlagMeetingChangedEvent`가 발행되는지. **시간이 그대로면 발행되지 않는지도 본다**
  (`Flag.isMeetingTimeChanged`가 이미 거른다)
- **통합 테스트 2개** — 둘 다 이벤트가 리스너까지 실제로 도달하는지를 보는 케이스다.
  단위 테스트로는 `@DomainEvents` 발행 여부를 잡을 수 없어서 이것들이 유일한 방어선이다
  - 후기 작성 시 `FlagConcludedEvent`가 `AFTER_COMMIT` 리스너에 도달하는지.
    `BEFORE_COMMIT`에서 발행한 이벤트가 전달된다는 전제를 확인한다.
    두 번째 후기 작성에는 발행되지 않는 것도 함께 본다
  - 일정 변경 시 `FlagMeetingChangedEventListener`가 실제로 깨어나는지
- 실행: `./gradlew test --tests '*Flag*'`

## Out of Scope

- **미수락 초대자 알림.** 수신 대상은 `flag_participants` 행뿐이다. task-101 영역
- **앵콜 자식 플래그 참여자 알림.** 부모 삭제 시 `severParentLink()`만 한다. 현행 유지
- **`InteractionType` 선택을 social로 옮기기.** flag가 `FLAG_ENDED`/`FLAG_ENDED_ENCORE`를
  고르는 구조는 유지한다. 결합을 더 끊으려면 social이 `FlagConcludedEvent`를 직접 받아야
  하는데 이 task의 범위를 넘는다
- **알림·이벤트 발행의 멱등성.** 4절에서 감수하기로 한 사안이다
- **`deletedAt` 과적재 해소.** 호스트 취소와 자동 아카이브가 같은 컬럼을 쓰는 것이
  `statusAtDeletion` 추정의 근본 원인이다. 구조를 손볼 때의 출발점이지만 이 task는
  그 위에서 동작을 바로잡는 데까지만 한다
