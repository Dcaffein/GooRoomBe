# PLAN — task-97: Flag 도메인 정리

## 작업 목표

죽은 상태 표현 제거, 도메인 예외의 500 누수 차단, `FlagPreservationPolicy` 계층 위반 제거.
목적과 의사결정은 `harness/tasks/task-97-flag-domain-cleanup.md` 참조.

## 도메인 수정 승인 요청

| 대상 | 변경 |
|---|---|
| `FlagInvitation` | `status`·`expiresAt` 필드 삭제, `isPending`·`isExpired`·`validatePending`·`validateNotExpired` 삭제 |
| `FlagInvitationStatus` | enum 삭제 |
| `FlagStatus` | `isWaiting()`·`isInActivity()` 삭제 |
| `Flag` | raw 예외 4곳을 도메인 예외로 교체 |
| `FlagComment`·`FlagMemorial` | raw 예외 각 1곳 교체 |
| `FlagPreservationPolicy` | `@Transactional`·`save()` 제거 |
| 신규 예외 3개 | `FlagInvalidCapacityException`, `FlagCommentInvalidContentException`, `FlagMemorialInvalidContentException` |

DB 컬럼은 삭제하지 않는다. `ddl-auto: update`는 컬럼을 지우지 않으므로
`flag_invitations.status`·`expires_at`은 사용되지 않는 채로 남는다.

## 현황 분석

### 1. `FlagInvitation.status`가 변경되지 않는다

```java
private FlagInvitation(...) { this.status = FlagInvitationStatus.PENDING; }   // 유일한 대입

public void accept(Long requesterId) {
    validateInvitee(requesterId);
    validatePending();       // status는 항상 PENDING이라 발화 불가
    validateNotExpired();
}                            // 대입 없음 → 서비스가 deleteById
```

영향 범위 — `status`를 참조하는 곳 전부.

| 파일 | 내용 |
|---|---|
| `FlagInvitation:28,36,66,76` | 필드, 생성자 대입, `isPending()`, `validatePending()` |
| `FlagInvitationStatus` | `PENDING`만 쓰이고 `ACCEPTED`·`REJECTED`는 사용처 0 |
| `FlagInvitationJpaRepository:16,18,20,22` | 쿼리 메서드 3개 + JPQL의 `AND fi.status = 'PENDING'` |
| `FlagInvitationRepositoryAdapter:33,38,47,52` | `FlagInvitationStatus.PENDING` 인자 전달 |
| `FlagInvitationRepository:16,17` | 포트 메서드 이름의 `Pending` 어휘 |
| `FlagInvitationManager:65`, `FlagEncoreInvitationListener:40` | 호출부 |

### 2. `expiresAt`이 deadline 스냅샷이다

`FlagInvitation.create(flagId, inviterId, inviteeId, flag.getSchedule().getDeadline())`.
`PUT /schedule`로 deadline이 바뀌어도 갱신되지 않는다.
권위 있는 차단은 `accept` → `participateByInvitation` → `Flag.participate()`의
`isRecruiting()`이 수행한다.

### 3. raw JDK 예외 7곳

`BusinessException`이 아니어서 `GlobalExceptionHandler`의 캐치올 → 500.

| 위치 | 예외 | 도달 |
|---|---|---|
| `FlagComment:87` 내용 길이 | `IllegalArgumentException` | **가능** — Comment DTO에 `@Size` 없음 |
| `Flag:120` 모집 종료 후 탈퇴 | `IllegalStateException` | **가능** |
| `Flag:100` 호스트 자기참여 | `IllegalStateException` | 불가 — 친구 검사·초대 시 호스트 제외 |
| `Flag:127` 이미 삭제됨 | `IllegalStateException` | 불가 — `@SQLRestriction` |
| `Flag:225` capacity < 1 | `IllegalArgumentException` | 불가 — `@Min(1)` |
| `FlagMemorial:35` 내용 길이 | `IllegalArgumentException` | 불가 — `@Size(max=1000)` |
| `FlagParticipant:40` 널 검사 | `IllegalArgumentException` | 불가 — **유지 대상** |

### 4. `FlagPreservationPolicy`

```java
@Component @RequiredArgsConstructor
@Transactional                                   // 도메인에 들어온 트랜잭션 관리
public class FlagPreservationPolicy {
    public void refresh(Long flagId) {
        Flag flag = flagRepository.findById(flagId).orElseThrow(...);
        boolean isPreserved = memorialRepository.existsByFlagId(flagId)
                           || flagRepository.existsByParentId(flagId);
        flag.updateAutoExpiryExempt(isPreserved);
        flagRepository.save(flag);               // findById가 관리 상태 → 중복
    }
}
```

호출부 3곳 모두 트랜잭션 안이다 — `FlagMemorialEventListener`·`FlagEncoreEventListener`는
`BEFORE_COMMIT`(원본 트랜잭션), `FlagDeletionEventListener`는 `AFTER_COMMIT` +
리스너의 `REQUIRES_NEW`.

## 변경 파일 목록

| 파일 | 할 일 |
|------|------|
| `FlagInvitation.java` | `status`·`expiresAt` 및 관련 메서드 4개 삭제, `create()` 시그니처에서 `expiresAt` 제거 |
| `FlagInvitationStatus.java` | 삭제 |
| `FlagInvitationExpiredException.java` | 삭제 |
| `FlagInvitationRepository.java` | `existsByFlagIdAndInviteeId`, `findInviteeIdsByFlagId`로 리네임 |
| `FlagInvitationRepositoryAdapter.java` | `PENDING` 인자 제거, 리네임 반영 |
| `FlagInvitationJpaRepository.java` | 쿼리 메서드에서 `AndStatus` 제거, JPQL의 status 조건 제거 |
| `FlagInvitationManager.java` | `create()` 호출 인자, `existsPending...` 호출 |
| `FlagEncoreInvitationListener.java` | `findPendingInviteeIdsByFlagId` 호출 |
| `FlagStatus.java` | `isWaiting()`·`isInActivity()` 삭제 |
| `CommentCreateRequest.java`, `CommentUpdateRequest.java` | `@Size(max = 500)` 추가 |
| `Flag.java` | 라인 100·120·127·225의 예외 교체 |
| `FlagComment.java`, `FlagMemorial.java` | `validateContent`의 예외 교체 |
| `FlagInvalidCapacityException.java` | 신규 — `FlagException`, 400 |
| `FlagCommentInvalidContentException.java` | 신규 — `FlagCommentException`, 400 |
| `FlagMemorialInvalidContentException.java` | 신규 — `FlagMemorialException`, 400 |
| `FlagPreservationPolicy.java` | `@Transactional`·`save()` 제거 |

테스트 — `FlagInvitationControllerTest`, `FlagCommentControllerTest`,
`FlagMemorialControllerTest`, `FlagControllerTest`,
`FlagCommentCommandServiceTest`, `FlagMemorialCommandServiceTest`,
`FlagPreservationPolicyTest`, 초대 관련 서비스·도메인 테스트.

## 구현 방향

### 예외 매핑

| 위치 | 변경 후 | 상태 |
|---|---|---|
| `Flag:100` 호스트 자기참여 | `FlagAuthorizationException` | 403 |
| `Flag:120` 모집 종료 후 탈퇴 | `FlagInvalidStatusException` | 409 |
| `Flag:127` 이미 삭제됨 | `FlagInvalidStatusException` | 409 |
| `Flag:225` capacity < 1 | `FlagInvalidCapacityException` (신규) | 400 |
| `FlagComment:87` | `FlagCommentInvalidContentException` (신규) | 400 |
| `FlagMemorial:35` | `FlagMemorialInvalidContentException` (신규) | 400 |
| `FlagParticipant:40` | **변경 없음** | — |

신규 3개는 각 도메인의 추상 예외를 상속한다. 기존 예외 중 400을 쓰는 것이 없어
(`FlagScheduleInvalidException`도 409) 재사용할 수 없다.

### DTO 검증과 도메인 검증을 겹쳐 둔다

Comment DTO에 `@Size(max = 500)`을 붙여도 `FlagComment.validateContent`는 남긴다.
DTO 검증은 HTTP 경로만 막고, 시드 컨트롤러·이벤트 리스너 같은 내부 경로는 도메인이 막는다.
컬럼도 `length = 500`이라 세 곳의 상한이 일치해야 한다.

### `FlagInvitation.create()` 시그니처

```java
// 현재
create(Long flagId, Long inviterId, Long inviteeId, LocalDateTime expiresAt)
// 변경 후
create(Long flagId, Long inviterId, Long inviteeId)
```

`FlagInvitationManager.invite()`에서 `flag.getSchedule().getDeadline()` 인자를 제거한다.
`invite()`가 이미 `flag.isRecruiting()`을 검사하므로 생성 시점 차단은 유지된다.

### `FlagPreservationPolicy`의 트랜잭션 계약

`@Transactional`과 `save()`를 함께 제거하면 **호출자의 트랜잭션 안에서 호출되어야 한다**는
계약이 생긴다. 밖에서 호출하면 조용히 저장되지 않는다.
클래스 주석 대신 테스트로 고정한다 — 아래 테스트 전략 참조.

클래스 위치와 조회·저장 책임은 그대로 둔다. `Flag.updateAutoExpiryExempt`가
package-private이라 옮기면 `public`으로 열어야 한다.

## 예상 사이드 이펙트

### 응답 변경 3건

| 경로 | 현재 | 변경 후 |
|---|---|---|
| Comment 501자 작성·수정 | `500` | `400` + `validation.content` |
| 모집 종료 후 탈퇴 | `500` | `409` |
| 만료된 초대 수락 | `409 FlagInvitationExpiredException` | `409 FlagDeadlinePassedException` |

셋째는 상태 코드가 같고 본문의 `error`·`message`만 바뀐다.
FE가 예외 이름으로 분기 중이면 영향이 있다.

### DB에 남는 컬럼

`flag_invitations.status`·`expires_at`은 `ddl-auto: update`가 지우지 않으므로
사용되지 않는 채 남는다. `status`는 `nullable = false`이나 기존 행에 값이 있고
신규 INSERT에서 컬럼이 빠지므로 **기본값이 없으면 INSERT가 실패한다.**
적용 전 `flag_invitations` 테이블의 `status`·`expires_at`에 DEFAULT가 있는지 확인하고,
없으면 컬럼 삭제 또는 DEFAULT 부여가 선행되어야 한다.

### 영향 없음

- URL·API 경로 — 무변경
- `FlagSchedule.deadline`과 `WAITING` 상태 — 무변경
- 다른 도메인 — flag 외부에서 `FlagInvitationStatus`를 참조하는 코드 없음

## 테스트 전략

`TESTING-GUIDE.md` 기본 프로토콜을 따른다. 아래만 추가한다.

**응답 변경 3건 회귀** — Comment 501자 → 400 + `validation.content`,
모집 종료 후 탈퇴 → 409, 만료된 초대 수락 → 409.

**status 제거 후 중복 초대 차단** — `existsByFlagIdAndInviteeId`가 status 조건 없이도
중복을 거르는지. 기존 초대가 삭제되면 재초대가 가능해야 한다.

**`FlagPreservationPolicy` 통합 테스트** — 기본 프로토콜에서 벗어나는 유일한 항목.
현재 `FlagPreservationPolicyTest`는 mock 기반이라 `save()` 제거 후 더티 체킹으로
반영되는지 잡지 못한다. Testcontainers로 트랜잭션 안에서 `refresh()`를 호출하고
커밋 후 `is_preserved`가 실제로 갱신됐는지 확인한다.
**이번 작업에서 유일하게 Docker가 필요하다.**

## 커밋 구성

| | 내용 | 응답 변경 |
|---|---|---|
| 1 | `FlagInvitation.status` 및 쿼리 어휘 제거 | 없음 |
| 2 | `expiresAt` 제거, 죽은 `FlagStatus` 헬퍼 제거 | 만료 시 예외 타입 |
| 3 | 도메인 예외 정리 + Comment DTO `@Size` | 500 → 400·409 |
| 4 | `FlagPreservationPolicy` 계층 정리 | 없음 |

## 브랜치

기존 `ai/refactor-flag-controller-url-ownership`에 이어서 쌓는다.
`main` 분기 규칙에서 벗어나며 사유는 task-97 「기존 브랜치에 이어 쌓는다」 참조.
