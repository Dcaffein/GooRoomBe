# PLAN — flag 저장소 계층 정리

## 작업 목표

포트 4개, 어댑터 5개, JPA 리포지토리 5개에서 무력화된 트랜잭션 분리, 자식 수명 불일치,
죽은 코드, 과다 조회, 이름 불일치를 정리하고 테스트 공백을 채운다. API 표면은 무변경이다.

`ai/refactor-flag-controller-url-ownership`(URL 6 + 도메인 5 커밋)에 이어서 쌓는다.

**도메인 수정 승인 요청** — 포트가 도메인 패키지에 있어 아래 두 파일을 수정한다.
엔티티·규칙은 무변경이다.

- `domain/flag/repository/FlagRepository.java` — `existsById`·`deleteAllParticipants` 삭제,
  `findByIdExclusive` 리네임
- `domain/invitation/repository/FlagInvitationRepository.java` — `hardDeleteByFlagIdsIn` 삭제

---

## 1. 퍼지의 청크별 트랜잭션 분리가 무력화돼 있다

### 문제

```java
// FlagHardPurgeService:19
@Transactional
public void sweepExpiredData() { ... maintenancePort.purgeFlagsAndRelatedData(targets); }

// FlagMaintenanceAdapter:45 — 500건씩 끊어 별도 트랜잭션을 의도
transactionTemplate.execute(status -> { ...5개 테이블 삭제... });
```

`TransactionTemplate`의 기본 전파는 `REQUIRED`다. 호출자가 이미 트랜잭션을 열었으므로
**청크마다 바깥 트랜잭션에 합류한다.** 최대 5000건 × 5테이블 삭제가 트랜잭션 하나에서
일어나며, `transactionTemplate`을 주입한 목적이 달성되지 않는다.

### 방향

둘을 함께 바꿔야 한다. 하나만으로는 의도가 서지 않는다.

```java
// FlagHardPurgeService — @Transactional 제거
public void sweepExpiredData() { ... }

// FlagMaintenanceAdapter — 전파를 명시
transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
```

`REQUIRES_NEW`를 명시하면 호출자가 나중에 다시 `@Transactional`을 붙여도 청크 분리가
유지된다. `@Transactional` 제거만으로는 그 보호가 없다.

### 영향 — 동작 변화

지금은 중간 실패 시 전부 롤백된다. 변경 후에는 성공한 청크가 남는다.
퍼지는 멱등하므로(이미 지워진 id는 다음 실행에서 조회되지 않는다) 부분 성공이 안전하며,
대량 삭제가 하나의 롱 트랜잭션이 되는 것을 막는다.

---

## 2. 참여자만 자식 수명 규칙에서 벗어나 있다

### 문제

이 도메인의 삭제 전략은 **부하 분산**이다. 사용자 삭제와 자동 만료는 Flag에 `deleted_at`만
기록하고(가벼운 UPDATE), 무거운 다중 테이블 정리는 널널한 주기의 배치로 미룬다.

```
FlagLabelingScheduler   0 0 0/6 * * *   6시간마다   soft delete
FlagSweepingScheduler   0 0 3 * * *     하루 1회    hard delete + 자식 청소
```

참여자만 이 규칙에서 빠져 **두 번 삭제된다.**

```java
// FlagDeletionEventListener:58 — 삭제 직후 즉시
flagRepository.deleteAllParticipants(event.flagId());

// FlagMaintenanceAdapter:46 — 3시 배치에서 다시
participantRepositoryAdapter.hardDeleteByFlagIdsIn(chunk);
```

게다가 **자동 만료 경로에는 즉시 삭제가 아예 없다.** `expireAllExceedingThreshold`는
벌크 UPDATE라 엔티티 생명주기를 건너뛰고 `FlagDeletedEvent`가 발행되지 않는다.

| 삭제 경로 | 참여자 삭제 시점 |
|---|---|
| 유저가 직접 삭제 | 즉시 + 배치에서 또 |
| 스케줄러 자동 만료 | 배치에서만 |

배치만으로 충분하다는 것을 자동 만료 경로가 이미 증명하고 있다.

> 벌크 UPDATE가 이벤트를 건너뛰는 것 자체는 올바르다. 이벤트가 발행됐다면 모임 종료
> 24시간 뒤에 "모임이 호스트 사정으로 취소되었습니다" 알림이 나갔을 것이다.

### 방향

`FlagDeletionEventListener:58`의 `deleteAllParticipants` 호출을 제거하고 배치가 정리하게 한다.
남는 메서드는 알림·상호작용 이벤트 발행만 하므로 이름을 바꾼다.

```java
// processParticipantCleanup → notifyParticipants
private void notifyParticipants(FlagDeletedEvent event, Long hostId) {
    List<Long> participantIds = flagRepository.findAllParticipantIds(event.flagId());
    ...
}   // deleteAllParticipants 호출 제거
```

### 안전성 확인

`isParticipating` 호출 6곳 중 5곳은 Flag를 먼저 로드하거나(`findHostIdById`,
`findByIdExclusive`, `findById`) 이미 로드된 `Flag`를 받으므로 `@SQLRestriction`에 걸러진다.
`findFlagIdsByParticipantId`는 뒤이은 `findAllByIdIn`에서 걸러진다.

### 함께 — `participateByInvitation`의 검사 순서

```java
// FlagParticipationManager:36 — isParticipating이 Flag 로드보다 먼저다
if (flagRepository.isParticipating(flagId, userId)) throw new FlagParticipationDuplicateException(...);
Flag lockedFlag = flagRepository.findByIdExclusive(flagId).orElseThrow(...);
```

참여자가 12시간 남게 되면서, **소프트 삭제된 Flag에 이미 참여 중이면서 대기 중인 초대까지
가진 유저**가 그 초대를 수락하면 `404`가 아니라 `409`가 나간다.

도달하기 매우 어렵다 — `invite()`가 참여자를 초대 대상에서 막으므로
"초대받음 → 직접 참여 → 초대 미수락 → Flag 삭제 → 초대 수락" 순서여야 하고, 둘 다 에러 응답이다.
두 줄의 순서를 바꾼다. 잠금을 먼저 잡는 것이 동시성상으로도 맞다.

```java
Flag lockedFlag = flagRepository.findByIdExclusive(flagId).orElseThrow(...);
if (flagRepository.isParticipating(flagId, userId)) throw new FlagParticipationDuplicateException(...);
```

---

## 3. 죽은 포트 메서드 3개

### 문제

**`FlagRepository.existsById`** — 호출부 0.

**`FlagInvitationRepository.hardDeleteByFlagIdsIn`** — 포트로는 호출되지 않는다.
`FlagMaintenanceAdapter:49`가 `invitationJpaRepository`(JPA)를 직접 호출한다.
comment·memorial·participant·flag 포트에는 대응 메서드가 아예 없어, 다섯 중 초대만
짝 없는 메서드를 갖고 있다.

**`FlagRepository.deleteAllParticipants`** — 2번 적용 시 유일한 호출부가 사라진다.
어댑터 구현과 `FlagParticipantJpaRepository.deleteAllByFlagId`까지 연쇄로 비어버린다.

### 방향

포트 선언과 어댑터 구현을 삭제한다. `deleteAllByFlagId`(JPA)도 함께 삭제한다.
`hardDeleteByFlagIdsIn`(JPA)은 `FlagMaintenanceAdapter`가 쓰므로 유지한다.

---

## 4. `getFriendFlags`만 과다 조회한다

### 문제

```java
List<Flag> recruitingFlags = flagRepository.findByHostIdsAndDeadlineAfter(friendIds, now);
Map<Long, FlagUserInfo> hostInfoMap = flagUserPort.findUserInfosByIds(friendIds);  // 친구 전원
```

필요한 것은 `recruitingFlags`의 호스트뿐이다. 친구 150명 중 모집 중인 플래그를 가진 사람이
3명이면 147명분이 낭비된다. 같은 서비스의 `getRecentFlags`·`getHostingFlags`·
`getParticipatingFlags`는 모두 로드된 플래그에서 호스트를 추린다 — 이 메서드만 다르다.

### 방향

```java
List<Flag> recruitingFlags = flagRepository.findByHostIdsAndDeadlineAfter(friendIds, LocalDateTime.now());
if (recruitingFlags.isEmpty()) return List.of();

Set<Long> hostIds = recruitingFlags.stream().map(Flag::getHostId).collect(Collectors.toSet());
Map<Long, FlagUserInfo> hostInfoMap = flagUserPort.findUserInfosByIds(hostIds);
```

응답은 동일하다. 유저 조회 건수만 줄어든다.

---

## 5. 같은 잠금에 두 어휘

### 문제

| 포트 | 메서드 | 구현 |
|---|---|---|
| `FlagRepository` | `findByIdExclusive` | `@Lock(PESSIMISTIC_WRITE)` |
| `FlagCommentRepository` | `findByIdForUpdate` | `@Lock(PESSIMISTIC_WRITE)` |

### 방향

`findByIdForUpdate`로 통일한다. SQL `FOR UPDATE`와 직결되어 의미가 명확하고,
`Exclusive`는 무엇에 대한 배타인지 모호하다. 잠금은 도메인이 의도적으로 요청하는 것이므로
포트에 드러나는 것이 맞다고 본다.

호출부 3곳(`FlagManagementService`, `FlagParticipationManager` 2곳)을 함께 바꾼다.

---

## 6. 퍼지 처리량이 JPA 인터페이스에 박혀 있다

### 문제

```java
// FlagJpaRepository
@Query(value = "SELECT id FROM flags WHERE deleted_at < :bufferTime LIMIT 5000", nativeQuery = true)
List<Long> _findIdsInternal(@Param("bufferTime") LocalDateTime bufferTime);

default List<Long> findIdsByDeletedAtBefore(LocalDateTime bufferTime) {
    return _findIdsInternal(bufferTime);
}

// FlagMaintenanceAdapter:39
int chunkSize = 500;
```

**`5000`이 있어야 할 곳에 없다.** "1회 실행당 처리량 상한"이라는 운영 정책인데 JPA
인터페이스의 쿼리 문자열에 박혀 있다. 정책 소유자는 `bufferTime`(12시간)을 들고 있는
`FlagHardPurgeService`다. 조정하려면 JPA 인터페이스를 고쳐야 한다.

**네이티브를 쓴 이유가 코드에 없다.** `Flag`의 `@SQLRestriction("deleted_at IS NULL")`이
JPQL에 적용되어 찾으려는 소프트 삭제 행을 정확히 걸러내기 때문이다.

**`_` 접두 래퍼는 잔재다.** 도입 시점(`715cfd0`)에는 JPQL + `Pageable` 형태였고,
래퍼의 목적은 호출자에게 `PageRequest.of(0, 5000)`을 감추는 것이었다.

```java
// 715cfd0 시점
List<Long> _findIdsInternal(@Param("bufferTime") LocalDateTime bufferTime, Pageable pageable);
default List<Long> findIdsByDeletedAtBefore(LocalDateTime bufferTime) {
    return _findIdsInternal(bufferTime, PageRequest.of(0, 5000));
}
```

이후 네이티브로 바뀌며 `Pageable`이 사라지자 감출 것이 없어졌고 껍데기만 남았다.
지금은 두 메서드의 시그니처가 같다. `_` 접두 메서드는 프로젝트 전체에서 이것 하나뿐이며,
인터페이스 메서드는 모두 `public`이라 실제 접근 제한 효과도 없다.

### 방향

두 숫자는 성격이 달라 갈 곳이 다르다. 둘 다 호출자가 상수로 들고, 쿼리에는 파라미터로 넘긴다.
다른 쿼리 메서드와 같은 형태가 된다.

| 값 | 의미 | 소유자 |
|---|---|---|
| `5000` | 1회 실행당 처리량 상한 — **운영 정책** | `FlagHardPurgeService` |
| `500` | 1트랜잭션당 삭제 건수 — **영속성 세부** | `FlagMaintenanceAdapter` |

```java
// FlagJpaRepository — 래퍼 제거, 이름 하나, 이유 명시
// Flag의 @SQLRestriction("deleted_at IS NULL")이 JPQL에 적용되어 대상 행을 걸러내므로
// 네이티브 쿼리로 우회한다.
@Query(value = "SELECT id FROM flags WHERE deleted_at < :bufferTime LIMIT :batchSize", nativeQuery = true)
List<Long> findIdsByDeletedAtBefore(@Param("bufferTime") LocalDateTime bufferTime,
                                    @Param("batchSize") int batchSize);

// FlagMaintenancePort
List<Long> findIdsReadyForHardDelete(LocalDateTime bufferTime, int batchSize);

// FlagHardPurgeService
private static final int BUFFER_HOURS = 12;
private static final int BATCH_SIZE = 5000;

// FlagMaintenanceAdapter — 지역 변수를 상수로
private static final int CHUNK_SIZE = 500;
```

`application.yml`은 건드리지 않는다. 운영에서 이 값을 조정해야 했던 적이 없고,
하루 한 번 도는 배치라 설정 표면을 늘릴 근거가 없다.

### 알아둘 것 — 처리량 천장

소프트 삭제는 6시간마다, 하드 삭제는 하루 1회 최대 5000건이다.
**하루 소프트 삭제가 5000건을 넘으면 잔여분이 누적되어 따라잡지 못한다.**
현재 규모에선 무관하나, `BATCH_SIZE`를 서비스 상수로 올리면 조정 지점이 한곳에 생긴다.

---

## 7. `FlagMaintenanceAdapter` 필드명이 타입과 어긋난다

### 문제

```java
private final FlagParticipantJpaRepository participantRepositoryAdapter;   // JPA인데 Adapter
private final FlagMemorialJpaRepository    memorialRepositoryAdapter;
private final FlagCommentJpaRepository     commentRepositoryAdapter;
private final FlagInvitationJpaRepository  invitationJpaRepository;         // 이것만 정확
```

계층 위반은 아니다. `FlagMaintenancePort`를 구현하는 정상 어댑터이며 어댑터가 JPA를
직접 쓰는 것은 맞다. 이름만 오해를 부른다.

### 방향

`participantJpaRepository`, `memorialJpaRepository`, `commentJpaRepository`로 바꾼다.

---

## 8. 저장소 계층 테스트 공백

### 문제

| 클래스 | 테스트 |
|---|---|
| `FlagJpaRepository` | 4건 |
| `FlagInvitationJpaRepository` | 5건 |
| `FlagParticipantJpaRepository` | 없음 |
| `FlagCommentJpaRepository` | 없음 |
| `FlagMemorialJpaRepository` | 없음 |
| `FlagMaintenanceAdapter` | 없음 |

`FlagMaintenanceAdapter`가 가장 위험하다. `hardDeleteByIdsIn`은 JPQL bulk delete이고
`@SQLRestriction`이 bulk 문에 적용되지 않는다는 Hibernate 동작에 기대고 있다.
이 가정이 틀리면 **퍼지가 조용히 아무것도 지우지 않는다.**

### 방향

신규 4개 모두 Testcontainers가 필요하다.

**`FlagMaintenanceAdapterTest`** — 가장 중요하다.
- 소프트 삭제된 Flag와 하위 데이터(참여자·댓글·추모글·초대)를 심고
  `purgeFlagsAndRelatedData` 후 다섯 테이블에서 모두 사라지는지
- **2번 적용 후 참여자가 배치에서 실제로 지워지는지** — 즉시 삭제를 제거하므로
  이 경로가 유일한 정리 수단이 된다
- `findIdsReadyForHardDelete`가 `deleted_at IS NULL` 행은 반환하지 않고
  버퍼 이전에 삭제된 행만 반환하는지

**`FlagParticipantJpaRepositoryTest`** — `countByFlagIdIn` 프로젝션이 flagId별 집계를
정확히 돌려주는지, 참여자 없는 flagId가 결과에서 빠지는지(호출부가 `getOrDefault(id, 0)`에
의존한다).

**`FlagCommentJpaRepositoryTest`** — `deleteTargetAndReplies`가 대상과 답글을 함께 지우고
다른 댓글은 남기는지.

**`FlagMemorialJpaRepositoryTest`** — `existsByFlagIdAndWriterId` 조합 조건.

---

## 손대지 않는 것

### 삭제 전략 자체

소프트 삭제 → 지연 하드 삭제는 **부하 분산 목적으로 타당하다.** 사용자 삭제는 가벼운
UPDATE 하나로 끝내고, 무거운 다중 테이블 정리를 4배 널널한 주기의 배치로 미룬다.
복구는 애초에 목표가 아니므로 자식을 하드 삭제하는 것도 일관된다.

12시간 창 동안 데이터가 새지 않는 것도 확인했다 — `FlagCommentQueryService:30`,
`FlagMemorialQueryService:31`이 Flag를 먼저 로드해 404를 내고,
`FlagInvitationQueryService:42,58`이 `flagMap.containsKey(...)`로 걸러낸다.

2번은 이 전략을 바꾸는 것이 아니라, 규칙에서 벗어난 참여자를 규칙 안으로 들이는 것이다.

### 포트의 네이밍 스타일 혼재

`FlagRepository`에 도메인 어휘(`isParticipating`, `countParticipants`)와 JPA 파생 이름
(`findAllByHostId`, `existsByParentId`)이 섞여 있다. 통일 방향에 판단이 갈리고 호출부가
넓어 별도 안건으로 둔다.

### `FlagRepository`가 Flag와 FlagParticipant를 함께 담당하는 것

`FlagParticipant`의 생성자가 package-private이고 `Flag.participate()`만 생성하므로
Flag 애그리거트 내부다. 애그리거트 루트당 리포지토리 하나가 맞다.

### 그 외

- 인덱스 — task-96
- 쿼리 패턴 — `FlagQueryService`는 4번 외에 N+1이 없다. 전부 배치 조회다
- 다른 도메인의 저장소 계층

---

## 변경 파일 목록

| 파일 | 항목 |
|------|------|
| `FlagHardPurgeService.java` | 1, 6 |
| `FlagMaintenanceAdapter.java` | 1, 6, 7 |
| `FlagDeletionEventListener.java` | 2 |
| `FlagParticipationManager.java` | 2 — 검사 순서, 5 — 호출부 |
| `FlagRepository.java` (포트) | 3, 5 |
| `FlagInvitationRepository.java` (포트) | 3 |
| `FlagRepositoryAdapter.java` | 3, 5 |
| `FlagInvitationRepositoryAdapter.java` | 3 |
| `FlagParticipantJpaRepository.java` | 3 — `deleteAllByFlagId` 삭제 |
| `FlagQueryService.java` | 4 |
| `FlagJpaRepository.java` | 5, 6 |
| `FlagMaintenancePort.java` | 6 — 시그니처 |
| `FlagManagementService.java` | 5 — 호출부 |
| `FlagDeletionEventListenerTest.java` | 2 — 즉시 삭제 검증 제거 |
| `FlagMaintenanceAdapterTest.java` | 8 — 신규 |
| `FlagParticipantJpaRepositoryTest.java` | 8 — 신규 |
| `FlagCommentJpaRepositoryTest.java` | 8 — 신규 |
| `FlagMemorialJpaRepositoryTest.java` | 8 — 신규 |

## 커밋 구성

| | 내용 | 동작 변화 |
|---|---|---|
| 1 | 퍼지 트랜잭션 분리 정상화 (1) | **부분 성공 허용** |
| 2 | 참여자 즉시 삭제 제거 (2) | **참여자 수명 연장** |
| 3 | 죽은 포트 메서드 3개 삭제 (3) | 없음 |
| 4 | 퍼지 처리량 파라미터화 + 필드명 (6, 7) | 없음 |
| 5 | 잠금 어휘 통일 (5) | 없음 |
| 6 | `getFriendFlags` 과다 조회 제거 (4) | 없음 |
| 7 | 저장소 계층 테스트 4종 (8) | 없음 |

2번과 3번은 순서가 고정된다 — 2번이 `deleteAllParticipants`를 죽여야 3번에서 지울 수 있다.

## 테스트 실행 범위

`TESTING-GUIDE.md` 프로토콜을 따른다.

신규 4종 + `FlagDeletionEventListenerTest`, `FlagQueryServiceTest`, `FlagJpaRepositoryTest`,
`FlagInvitationJpaRepositoryTest`, `FlagManagementServiceTest`, `FlagParticipationManagerTest`.
