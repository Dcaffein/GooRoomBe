# PLAN — 플래그 종료 이벤트 정리 (task-103) 구현 방법

브랜치: `ai/fix-flag-conclusion-events` (main에서 분기), 커밋 6개

---

## 커밋 1 — 삭제 알림 대상

`FlagDeletionEventListener`의 비교 대상 하나만 바꾼다.

```java
- event.statusAtDeletion() != FlagStatus.RECRUITING
+ event.statusAtDeletion() != FlagStatus.ENDED
```

**테스트:** 이 클래스는 알림 발행을 한 건도 검증하지 않고 있었다. 네 상태 케이스를 새로 짠다.

## 커밋 2 — 일정 변경 이벤트 유실

`FlagModificationService.reschedule`에 `save()`를 넣는다. Spring Data의 `@DomainEvents`는
리포지토리의 `save`/`delete` 호출에서만 발행되고 더티 체킹 플러시로는 발행되지 않는다.

`registerEvent` 등록 지점 셋을 호출부의 `save()` 여부와 대조했다. `closeFlag`와
`FlagHostService.encoreFlag`는 정상이고 `reschedule`만 빠져 있었다.

**테스트:** 목 리포지토리는 `save()`를 불러도 도메인 이벤트를 발행하지 않는다. 발행 유실은
컨텍스트를 띄워야 잡히므로 `FlagRescheduleEventIntegrationTest`를 만든다.
`@TestConfiguration`으로 이벤트를 받아 적는 `@EventListener` 빈을 두고 발행 자체를 본다.

## 커밋 3 — 종료 사실을 한 곳으로

```java
public record FlagConcludedEvent(Long flagId, Long hostId, Long parentId, List<Long> participantIds) {
    public FlagConcludedEvent {
        if (participantIds == null || participantIds.isEmpty()) throw new IllegalArgumentException(...);
    }
    public boolean isEncore() { return parentId != null; }
}
```

`FlagConclusionEventListener`가 `isEncore()`로 `InteractionType`을 고르고
`BatchMutualInteractionEvent`로 넘긴다. **리포지토리 의존이 없다.**
`@Transactional(REQUIRES_NEW)`는 DB 접근 때문이 아니라, 여기서 발행하는 이벤트를 받는 쪽이
`@TransactionalEventListener(AFTER_COMMIT)`이라 트랜잭션이 없으면 전달되지 않아서다.

`FlagDeletionEventListener`는 `isEmpty()` 검사를 앞으로 빼고 두 분기를 `if/else`로 묶는다.
`isMeetingHeld`, `publishInteractionEvents`, `InteractionType` import를 제거한다.

```java
if (event.statusAtDeletion() == FlagStatus.ENDED) {
    eventPublisher.publishEvent(new FlagConcludedEvent(..., participantIds));
} else {
    notifyFlagCancel(participantIds, event.flagTitle());
}
```

## 커밋 4 — 자동 만료와 면제 획득 경로

### 4-1. 리포지토리

`expireAllExceedingThreshold` 하나를 둘로 나눈다. **조회 조건은 기존 UPDATE의 WHERE와 같다.**
`@SQLRestriction`이 `deleted_at IS NULL`을 붙이므로 명시할 것은 둘뿐이다.

```java
@Query("SELECT f.id AS id, f.hostId AS hostId, f.parentId AS parentId FROM Flag f " +
       "WHERE f.schedule.endDateTime < :threshold AND f.autoExpiryExempt = false " +
       "ORDER BY f.schedule.endDateTime ASC")
List<FlagExpiryTarget> findExpiryTargets(LocalDateTime threshold, Pageable pageable);

@Modifying(clearAutomatically = true)
@Query("UPDATE Flag f SET f.deletedAt = :now WHERE f.id IN :ids AND f.deletedAt IS NULL")
int expireByIds(Collection<Long> ids, LocalDateTime now);
```

엔티티 대신 프로젝션으로 세 필드만 읽는다. 곧 같은 행을 벌크 UPDATE하므로 영속성
컨텍스트에 올리지 않는 편이 낫고, 이벤트가 `hostId`·`parentId`를 실어야 하는데 소프트 삭제
후에는 되읽을 수 없다.

참여자 묶음 조회를 추가한다. 단건 조회는 참여자 id만 돌려주므로 어느 플래그의 것인지 알 수
없어 묶음에 못 쓴다. `(flagId, participantId)` 쌍을 프로젝션으로 받아 어댑터에서 그룹핑한다.
`V3__add_flag_indexes.sql`의 `(flag_id, participant_id)`가 커버한다.

어댑터에서 `ids.isEmpty()`면 쿼리를 보내지 않는다. 빈 `IN ()`은 DB에 따라 문법 오류다.

### 4-2. 스윕

```java
List<FlagExpiryTarget> targets = flagRepository.findExpiryTargets(threshold, BATCH_SIZE);
List<Long> targetIds = targets.stream().map(FlagExpiryTarget::getId).toList();
Map<Long, List<Long>> participantsByFlagId = flagRepository.findAllParticipantIdsByFlagIds(targetIds);
int expiredFlags = flagRepository.expireByIds(targetIds, now);
targets.forEach(target -> publishConclusion(target, participantsByFlagId));
```

**대상이 몇 건이든 조회는 2회다.** 초대 정리가 소프트 삭제보다 먼저인 순서는 유지한다.
`BATCH_SIZE = 5000`을 두고, 참여자가 없는 플래그는 소프트 삭제만 하고 발행하지 않는다.

### 4-3. 면제 획득

```java
// Flag — 자기 상태 사실만 발행한다
void updateAutoExpiryExempt(boolean value) {
    if (value && !this.autoExpiryExempt) {
        registerEvent(new FlagExpiryExemptedEvent(this.id, this.hostId, this.parentId));
    }
    this.autoExpiryExempt = value;
}
```

`FlagExpiryExemptionEventListener`가 참여자를 조회해 `FlagConcludedEvent`로 번역한다.

`FlagExpiryExemptionPolicy`는 `Updater`로 개명하고 `Flag`를 반환한다. 호출부 셋
(`FlagMemorialEventListener` 생성·삭제, `FlagEncoreEventListener`, `FlagDeletionEventListener`)이
`flagRepository.save(...)`로 저장한다.

**호출부 셋이 `BEFORE_COMMIT` 리스너다.** 그 단계에서 `save()`가 도메인 이벤트를 실제로
발행하는지는 단위 테스트로 확인되지 않으므로 `FlagConclusionEventIntegrationTest`로 본다.

### 4-4. 테스트

- `FlagJpaRepositoryTest` — 새 프로젝션 쿼리를 실제 DB에서 검증(종료·면제·삭제 필터,
  `hostId`/`parentId` 채움, 상한, `expireByIds`). 기존 만료 쿼리에는 DB 레벨 테스트가 없었다
- `FlagParticipantJpaRepositoryTest` — `flag_id`별 그룹핑, 참여자 없는 플래그는 키 자체가 없음
- `FlagMemorialFactoryTest`(신규) / `FlagEncoreFactoryTest` — `isEnded()` 가드 고정
- **통합 테스트는 `@SpringBootTest`가 같은 Testcontainers MySQL에 커밋한다.** JPA 테스트가
  `containsExactly`로 단정하면 남의 행에 깨지므로 `contains` + `doesNotContain`으로 쓴다

## 커밋 5 — 주석

코드만 봐서는 알 수 없고 모르면 잘못 고치게 되는 것만 남긴다. 되돌리기 쉬운 자리(조회를
없애고 벌크 UPDATE로 합치기, 조건을 복사해 두 번 쓰기), 판단이 일어나는 자리
(`InteractionType` 선택, 면제→종료 번역), 감수한 지점(면제 재점화 시 재발행).

## 커밋 6 — `save()` 명시

flag 도메인에서 변경만 하고 저장을 안 부르던 여섯 곳을 채운다.

`FlagModificationService`의 `modifyFlagDetails`·`modifyFlagCapacity`·`closeRecruitment`,
`FlagCommentCommandService.updateComment`, `FlagMemorialCommandService.updateMemorial`,
`FlagInvitationCommandService.updateInvitePermission`.

마지막 건은 `FlagInvitationManager.updateInvitePermission`이 `FlagParticipant`를 반환하도록
바꾼다. 여섯 곳 모두 도메인 이벤트를 등록하지 않아 동작은 바뀌지 않는다.

---

## 확인해둔 사실

- `BatchMutualInteractionEvent` 발행 지점은 작업 후 `FlagConclusionEventListener` 하나다
- 보존 원천 둘이 모두 종료 이후에만 발생한다(`FlagMemorialFactory`, `Flag.createEncore`).
  커밋 4에서 이 가드를 테스트로 고정했다
- `flag_participants`에는 `@SQLRestriction`이 없어 소프트 삭제 후에도 조회된다
- social은 한 줄도 바뀌지 않는다. 받는 이벤트 타입과 페이로드가 그대로다
- 스키마 변경 없음. 마이그레이션 파일을 추가하지 않는다
