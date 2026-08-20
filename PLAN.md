# PLAN — flag 서비스 계층 정리

## 작업 목표

flag 애플리케이션 계층(서비스 12, 리스너 6)에서 잠금 순서 결함 1건, 과다 쿼리 1건,
거짓 분기 1건, 잉여 코드 1건, 패키지·이름 불일치 2건을 정리하고 테스트 공백 하나를 채운다. API 표면은 무변경이다.

`ai/refactor-flag-controller-url-ownership`(URL 6 + 도메인 5 + 저장소 8 + docs 1)에 이어서 쌓는다.

**도메인 수정 없음.** 엔티티·도메인 서비스를 건드리지 않는다. 포트는 2번에서 죽는 메서드 하나만 지운다.

---

## 1. `modifyFlagCapacity`가 잠금 밖에서 센 값을 쓴다

### 문제

```java
// FlagManagementService:29-32
int currentCount = flagRepository.countParticipants(command.flagId());   // 잠금 전에 센다
Flag flag = flagRepository.findByIdForUpdate(command.flagId())           // 그 다음 잠근다
        .orElseThrow(() -> new FlagNotFoundException(command.flagId()));
flag.updateCapacity(command.hostId(), command.capacity(), currentCount);
```

같은 Flag 행을 잠그는 참여 경로는 순서가 반대다.

```java
// FlagParticipationManager:25-31 — 잠그고 나서 센다
Flag lockedFlag = flagRepository.findByIdForUpdate(flagId).orElseThrow(...);
int count = flagRepository.countParticipants(flagId);
return lockedFlag.participate(userId, count);
```

두 경로는 잠금으로 직렬화되지만, 정원 변경 쪽이 잠금 밖에서 읽은 값을 들고 들어간다.

```
T1(정원변경)  countParticipants → 5
T2(참여)      잠금 획득 → count=5, capacity=10 → 참여 → 6명, 커밋
T1            잠금 획득 → updateCapacity(newCapacity=5, currentCount=5)
              검증: newCapacity < currentCount → 5 < 5 → false → 통과
              결과: 참여자 6명, 정원 5
```

### 방향

`countParticipants`를 잠금 뒤로 옮긴다.

```java
Flag flag = flagRepository.findByIdForUpdate(command.flagId())
        .orElseThrow(() -> new FlagNotFoundException(command.flagId()));
int currentCount = flagRepository.countParticipants(command.flagId());
flag.updateCapacity(command.hostId(), command.capacity(), currentCount);
```

저장소 작업의 `participateByInvitation` 교정과 같은 성격이다.

---

## 2. `getMemorials`가 쿼리 5개를 쏜다

### 문제

```java
// FlagMemorialQueryService:31-44
findHostIdById              1
isParticipating             2
existsByFlagId              3     ← 없으면 empty()
existsByFlagIdAndWriterId   4     ← 내가 안 썼으면 asLocked()
findAllByFlagId             5
```

`MemorialListResult.asLocked()`는 `List.of()`를 돌려준다. 즉 3·4는 5가 이미 가진 정보를
두 번 더 물어보는 것이다.

### 방향

한 번 로드하고 메모리에서 가른다. 5개 → 3개.

```java
List<FlagMemorial> memorials = memorialRepository.findAllByFlagId(flagId);
if (memorials.isEmpty()) return MemorialListResult.empty();
if (memorials.stream().noneMatch(m -> m.getWriterId().equals(viewerId))) {
    return MemorialListResult.asLocked();
}
```

잠긴 경우 몇 행을 헛로드하지만, 한 모임의 후기라 건수가 적어 쿼리 두 개를 줄이는 편이 낫다.

`existsByFlagIdAndWriterId`는 이 호출부가 유일하므로 포트·어댑터·JPA에서 함께 죽는다.
`existsByFlagId`는 `FlagPreservationPolicy`가 계속 쓰므로 유지한다.

---

## 3. `invite()`에 도달할 수 없는 거짓 분기가 있다

### 문제

```java
// FlagInvitationService:32-37
FlagInvitation invitation = invitationManager.invite(flagId, inviterId, inviteeId);
//   └ FlagInvitationManager:41 에서 flagRepository.findById(flagId).orElseThrow(...) 로 이미 검증됨
String flagTitle = flagRepository.findById(flagId)
        .map(f -> f.getTitle())
        .orElse("");            // ← 도달할 수 없다
```

같은 트랜잭션이라 두 번째 조회는 1차 캐시 히트여서 SQL은 나가지 않는다.
문제는 `.orElse("")`가 "없을 수도 있다"고 말한다는 점이다. 만약 도달하면
`[] 플래그에 초대받았습니다`라는 알림이 나간다.

### 방향

```java
String flagTitle = flagRepository.findById(flagId)
        .map(Flag::getTitle)
        .orElseThrow(() -> new FlagNotFoundException(flagId));
```

두 번째 조회 자체를 없애려면 `FlagInvitationManager.invite`의 반환 타입을 바꿔야 하는데,
캐시 히트라 얻는 것이 없어 하지 않는다.

---

## 4. `occurredAt`을 불필요하게 채운다

### 문제

`NotificationEvent`의 컴팩트 생성자가 기본값을 채운다.

```java
public NotificationEvent {
    if (occurredAt == null) occurredAt = LocalDateTime.now();
```

그런데 리스너마다 다르다.

| 리스너 | `.occurredAt(...)` |
|---|---|
| `FlagDeletionEventListener` | 명시 |
| `FlagMeetingChangedEventListener` | 명시 |
| `FlagInvitationEventListener` | 없음 |

동작은 셋 다 같다. 명시한 쪽이 잉여이며 없는 쪽과 어긋나 보인다.

### 방향

두 곳에서 `.occurredAt(LocalDateTime.now())`를 제거해 셋을 맞춘다.

---

## 5. 서비스 패키지와 이름 규칙이 어긋난 두 곳

### 문제

도메인은 네 폴더인데 서비스는 셋이다. `invitation`만 `flag/` 안에 섞여 있다.

```
domain/               comment/  flag/  invitation/  memorial/
application/service/  comment/  flag/               memorial/     ← invitation 없음
```

이름 규칙도 같은 지점에서 어긋난다. 조회 쪽은 넷 다 `QueryService`로 일관되고
전부 `@Transactional(readOnly = true)`인데, 명령 쪽만 갈린다.

| 도메인 | 명령 | 조회 |
|---|---|---|
| comment | `FlagCommentCommandService` | `FlagCommentQueryService` |
| memorial | `FlagMemorialCommandService` | `FlagMemorialQueryService` |
| invitation | **`FlagInvitationService`** | `FlagInvitationQueryService` |
| flag | `FlagHostService` · `FlagManagementService` · `FlagParticipationService` | `FlagQueryService` |

`invitation`은 comment·memorial과 같은 CQRS 짝인데 `Command`만 빠져 있다.

**`FlagManagementService`는 서비스 37개 중 유일한 `*ManagementService`다.** 프로젝트의
다른 서비스는 전부 주제 + 동작으로 이름 짓는다 — `FriendshipDecayService`,
`FriendshipArchiveService`, `UserOutboxCleanupService`, `FriendRequestReceiverActionService`.
`Manager` 접미사는 도메인 협력자(`FlagInvitationManager`)와 인프라(`AuthCookieManager`)에만 쓰인다.

### 방향

폴더를 만들고 두 서비스를 옮기면서 이름을 맞춘다.

```
application/service/invitation/
├── FlagInvitationCommandService.java   ← flag/FlagInvitationService.java
└── FlagInvitationQueryService.java     ← flag/FlagInvitationQueryService.java
```

comment·memorial과 형태가 같아지고, 서비스 폴더가 도메인 폴더를 그대로 미러링한다.

포트 `FlagInvitationUseCase`는 그대로 둔다 — 포트 이름은 컨트롤러가 쓰는 계약이고
`FlagInvitationQueryUseCase`와 이미 구분된다.

`FlagManagementService` → `FlagModificationService`. 말씀된 축(참여가 아닌 Flag 자체)을
유지하면서 동작을 말한다. `closeFlag`는 `deleted_at`을 찍는 상태 변경이라 "변경"에 포함된다.

**포트도 함께 바꾼다** — `FlagManagementUseCase` → `FlagModificationUseCase`.
안 바꾸면 서비스와 포트 이름이 어긋나는 새 불일치가 생긴다.
영향은 포트·구현·`FlagController` 필드·테스트이며 순수 리네임이다.

최종 형태:

```
FlagHostService           생성
FlagModificationService   변경
FlagParticipationService  참여
FlagQueryService          조회
```

### 손대지 않는 것

**flag 명령 서비스를 하나로 합치는 것.** 축이 셋이다 — 생성(Host), 변경(Modification),
참여(Participation). `FlagCommandService` 하나로 합치면 컨트롤러에서 없앤 CQRS 축을
서비스에 되살리는 셈이 된다.

**배치·시드 서비스의 위치.** `FlagExpiryService`·`FlagPurgeService`·`FlagSeedService`는
Flag 애그리거트를 다루므로 `flag/`가 맞다. 별도 폴더로 빼면 도메인에 없는 분류를
서비스 계층에만 만들게 된다.

---

## 6. 배치 두 경로가 자기를 부르는 말이 셋씩이다

### 문제

```
FlagLabelingScheduler.runLabeling()  → FlagExpiryService.labelExpiredFlags()   → soft delete
FlagSweepingScheduler.runSweeping()  → FlagHardPurgeService.sweepExpiredData() → hard delete
```

한 경로에 **Labeling / Expiry / label**, 다른 경로에 **Sweeping / HardPurge / sweep**.
`FlagPreservationPolicy` / `autoExpiryExempt` / `is_preserved`와 같은 어휘 분산이며 더 심하다.

게다가 어느 이름도 "삭제"라고 말하지 않는다. `labelExpiredFlags`는 라벨을 붙이는 것처럼
들리지만 실제로는 `deleted_at`을 찍는다.

### 방향

`expire`(소프트)와 `purge`(하드) 두 단어만 남기고 스케줄러·서비스·메서드를 맞춘다.

| | 현재 | 변경 후 |
|---|---|---|
| 소프트 삭제 | `FlagLabelingScheduler.runLabeling()` | `FlagExpiryScheduler.runExpiry()` |
| | `FlagExpiryService.labelExpiredFlags()` | `FlagExpiryService.expireEndedFlags()` |
| 하드 삭제 | `FlagSweepingScheduler.runSweeping()` | `FlagPurgeScheduler.runPurge()` |
| | `FlagHardPurgeService.sweepExpiredData()` | `FlagPurgeService.purgeExpiredFlags()` |

포트의 `purgeFlagsAndRelatedData`·`findIdsReadyForHardDelete`가 이미 purge/hardDelete를
쓰고 있어 하드 삭제 쪽 어휘가 맞아떨어진다.

`FlagExpiryService`의 로그 문구("시스템 자동 만료 처리 완료: {}건의 플래그에 deletedAt 기록")도
새 어휘에 맞춰 다듬는다.

7번에서 만드는 테스트는 `FlagPurgeServiceTest`로 이름을 맞춘다.

---

## 7. 테스트 공백

### 문제

| 클래스 | 테스트 |
|---|---|
| `FlagHardPurgeService` (→ `FlagPurgeService`) | 없음 |
| `FlagMeetingChangedEventListener` | 없음 |
| `FlagMemorialEventListener` | 없음 |
| `FlagEncoreEventListener` | 없음 |

나머지 14개는 2~15건씩 있다.

### 방향

**`FlagPurgeService`(6번에서 리네임)만 채운다.** 저장소 작업에서 `@Transactional`을 떼어낸 곳이라
호출 계약을 고정해둘 값이 있다. mock 기반이라 빠르다.

- 대상이 비어 있으면 `purgeFlagsAndRelatedData`를 부르지 않는지
- `bufferTime`과 `BATCH_SIZE`를 포트에 정확히 넘기는지

나머지 세 리스너는 위임 한 줄짜리라 넣지 않는다.

---

## 손대지 않는 것

### 트랜잭션 경계

조회 서비스는 전부 `@Transactional(readOnly = true)`, 명령 서비스는 `@Transactional`.
리스너 넷은 모두 `AFTER_COMMIT` + `REQUIRES_NEW`로 형태가 같다. 일관돼 있다.

### `encoreFlag`의 예외 변환

`FlagEncoreFactory`의 `existsByParentId` 검사와 서비스의
`DataIntegrityViolationException` 캐치는 중복이 아니라 경합을 막는 이중 방어다.

### 쿼리 패턴

2번 외에 N+1이 없다. `getCommentTree`도 유저 정보를 배치로 모은다.

### 그 외

- 포트 네이밍 스타일 통일, `FlagRepository` 분할 — task-98에서 범위 외로 둔 그대로
- 인덱스 — task-96
- 다른 도메인, 프론트엔드

---

## 변경 파일 목록

| 파일 | 항목 |
|------|------|
| `FlagManagementService.java` → `FlagModificationService.java` | 1, 5 — 리네임 |
| `FlagManagementUseCase.java` → `FlagModificationUseCase.java` | 5 |
| `FlagController.java` | 5 — 주입 필드 |
| `FlagMemorialQueryService.java` | 2 |
| `FlagMemorialRepository.java` (포트) | 2 — `existsByFlagIdAndWriterId` 삭제 |
| `FlagMemorialRepositoryAdapter.java` | 2 |
| `FlagMemorialJpaRepository.java` | 2 |
| `FlagInvitationService.java` | 3 |
| `FlagDeletionEventListener.java` | 4 |
| `FlagMeetingChangedEventListener.java` | 4 |
| `FlagManagementServiceTest.java` → `FlagModificationServiceTest.java` | 1 — 잠금 뒤 카운트 검증, 5 — 리네임 |
| `FlagMemorialQueryServiceTest.java` | 2 — 스텁 갱신 |
| `FlagMemorialJpaRepositoryTest.java` | 2 — 삭제된 메서드 테스트 제거 |
| `FlagInvitationServiceTest.java` → `invitation/FlagInvitationCommandServiceTest.java` | 3 — 스텁 갱신, 5 — 이동·리네임 |
| `FlagInvitationQueryServiceTest.java` | 5 — 이동 |
| `flag/FlagInvitationService.java` → `invitation/FlagInvitationCommandService.java` | 5 — 이동·리네임 |
| `flag/FlagInvitationQueryService.java` → `invitation/FlagInvitationQueryService.java` | 5 — 이동 |
| `FlagLabelingScheduler.java` → `FlagExpiryScheduler.java` | 6 |
| `FlagSweepingScheduler.java` → `FlagPurgeScheduler.java` | 6 |
| `FlagExpiryService.java` | 6 — 메서드명·로그 |
| `FlagHardPurgeService.java` → `FlagPurgeService.java` | 6 |
| `FlagExpiryServiceTest.java` | 6 — 메서드명 |
| `FlagPurgeServiceTest.java` | 7 — 신규 |

## 커밋 구성

| | 내용 | 동작 변화 |
|---|---|---|
| 1 | 정원 변경의 잠금 순서 교정 (1) | 경합 상황에서만 |
| 2 | `getMemorials` 쿼리 축소 + 죽은 포트 메서드 (2) | 없음 |
| 3 | `invite()` 거짓 분기 제거, `occurredAt` 잉여 제거 (3, 4) | 없음 |
| 4 | 서비스 패키지·이름 정리 + 배치 어휘 통일 (5, 6) | 없음 |
| 5 | `FlagPurgeServiceTest` 신규 (7) | 없음 |

## 테스트 실행 범위

`TESTING-GUIDE.md` 프로토콜을 따른다.

`FlagModificationServiceTest`, `FlagMemorialQueryServiceTest`, `FlagMemorialJpaRepositoryTest`,
`FlagInvitationCommandServiceTest`, `FlagPurgeServiceTest`, `FlagExpiryServiceTest`,
그리고 `FlagPreservationPolicyTest`(`existsByFlagId`를 계속 쓰는 유일한 호출부).
