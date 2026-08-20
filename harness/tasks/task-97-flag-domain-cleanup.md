# Task-97: Flag 도메인 정리

> **Domain Change:** [x] — 엔티티 필드 삭제, 예외 타입 교체, 도메인 서비스 수정을 포함한다.

## Objective

flag 도메인에서 세 가지를 걷어낸다.

1. **죽은 상태 표현** — `FlagInvitation.status`, `expiresAt`, 사용처 없는 `FlagStatus` 헬퍼
2. **도메인 예외가 500으로 새는 경로** — `BusinessException`을 상속하지 않은 raw JDK 예외
3. **`FlagPreservationPolicy`의 계층 위반** — 도메인 클래스의 `@Transactional`과 영속성 오케스트레이션

## Background

### 상태 없는 상태 필드

`FlagInvitation.status`는 생성자에서 `PENDING`이 대입된 뒤 어디서도 변경되지 않는다.
`accept`·`reject`·`cancel`은 검증만 하고 애플리케이션 서비스가 행을 삭제한다.
`ACCEPTED`·`REJECTED`는 한 번도 쓰이지 않고, 모든 `status = 'PENDING'` 필터는 전체 행과 같다.

같은 성격의 작업이 `afd964e`(조회 쿼리에서 FlagStatus 어휘 제거)에서 한 번 있었다.
리포지토리 메서드 이름의 `Pending` 어휘도 같은 원칙으로 걷는다.

`expiresAt`은 초대 시점 `flag.getSchedule().getDeadline()`의 복사본이라
호스트가 일정을 바꾸면 어긋난다. 실제 참여 차단은 `Flag.participate()`의
`isRecruiting()`이 수행하므로, 같은 질문에 부정확한 사본으로 답하는 셈이다.

### 500으로 나가는 비즈니스 규칙 위반

`GlobalExceptionHandler`는 `BusinessException`에서 `getHttpStatus()`를 읽고,
나머지는 `@ExceptionHandler(Exception.class)`로 받아 500을 반환한다.
도메인이 던지는 `IllegalStateException`·`IllegalArgumentException` 7곳이 여기 해당한다.

이 중 둘은 실제로 도달한다.

- Comment 501자 — `CommentCreateRequest`·`CommentUpdateRequest`에 `@Size`가 없다
  (Memorial DTO에는 있다)
- 모집 종료 후 탈퇴 — `Flag:120`

### 도메인에 들어온 트랜잭션 관리

`FlagPreservationPolicy`는 preservation 조건을 도메인에 캡슐화하려는 의도로 만들어졌고,
`Flag.updateAutoExpiryExempt`가 package-private이라 그 봉인은 이미 성립해 있다.
다만 `@Transactional`과 `flagRepository.save()`는 애플리케이션 계층의 관심사다.

## 의사결정

### `FlagSchedule.deadline`은 유지한다

`expiresAt`과 함께 지울 후보로 검토했으나 **불가**하다.
`deadline`은 `RECRUITING`과 `WAITING`을 가르는 유일한 값이며,
`WAITING` 구간(deadline~start)에서 참여·초대는 막히고 탈퇴·일정 변경은 열린다.
없애면 `closeRecruitment`(모집 조기 마감)를 표현할 방법이 사라진다 —
마감하려면 `start`를 당겨야 하는데 그건 모임 시간 변경이다.

### `FlagPreservationPolicy`는 도메인에 남긴다

조회·저장을 이벤트 리스너로 올리는 안을 검토했다가 **철회**했다.

- `Flag.updateAutoExpiryExempt`가 package-private이라 클래스를 옮기면 `public`으로 열어야 한다
- 규칙(`Memorial 존재 || 자식 Flag 존재`)이 필요로 하는 정보가 Flag 밖에 있어
  엔티티 안에 넣을 수 없다
- 조회를 올리면 "preservation이 무엇에 달렸는지"가 리스너 3개로 복사되어,
  요구사항 변동 시 수정 범위 파악이 어려워진다 — 이 클래스를 만든 목적과 정반대다

따라서 규칙 전체(어떤 사실이 필요한지 + 어떻게 조합하는지 + 결과 기록)를 한 클래스에 둔다.

### `save()`를 제거하고 트랜잭션 계약을 감수한다

`findById`가 관리 상태 엔티티를 반환하므로 더티 체킹으로 대체된다.
대신 **이 클래스는 호출자의 트랜잭션 안에서 호출되어야 한다**는 계약이 생기고,
밖에서 호출하면 조용히 저장되지 않는다. 현재 호출부 셋은 모두 만족한다.

남기는 안도 검토했으나(밖에서 호출해도 안전) 영속성 오케스트레이션을 도메인에서
빼는 쪽을 택했다.

### `FlagParticipant`의 널 검사는 raw 예외로 둔다

`flagId`·`participantId` 널은 사용자 입력이 아니라 프로그래밍 오류다.
`IllegalArgumentException`이 맞다.

### 도달 불가한 방어 코드도 함께 바꾼다

`Flag:100`, `Flag:127`, `Flag:225`, `FlagMemorial:35`는 앞단 검증이 막고 있어
현재 응답이 바뀌지 않는다. 그래도 같은 결함이므로 함께 정리한다.
시드 컨트롤러나 이벤트 리스너처럼 DTO 검증을 거치지 않는 내부 경로에서 발화하면
그때는 500이 나간다.

### 기존 브랜치에 이어 쌓는다

`ai/refactor-flag-controller-url-ownership`에 URL 정리 커밋 6개가 있고 미병합이다.
flag 관련 미병합 브랜치는 이것 하나뿐이다.
URL 변경 때문에 이미 FE와의 배포 조율이 필요하고, 이 작업도 응답 상태 코드를 바꾸므로
같은 릴리스에 묶는 편이 조율 횟수를 줄인다.
WORKFLOW의 `main` 분기 규칙에서 벗어나는 부분이며 사용자 승인을 받았다.

## 알려진 제약

### 응답이 바뀌는 지점

- Comment 501자: `500` → `400` (+ `validation.content`)
- 모집 종료 후 탈퇴: `500` → `409`
- 만료된 초대 수락: `FlagInvitationExpiredException` → `FlagDeadlinePassedException`.
  상태 코드는 409로 같으나 **에러 본문의 `error` 필드와 메시지가 달라진다.**
  FE가 예외 이름으로 분기 중이라면 영향이 있다.

### 더티 체킹은 mock 테스트로 검증되지 않는다

`FlagPreservationPolicyTest`가 mock 기반이라 `save()` 제거 후 실제 반영 여부를 잡지 못한다.
Testcontainers 통합 테스트가 필요하다. 이번 작업에서 유일하게 Docker가 필요한 부분이다.

## Out of Scope

- **URL 구조** — 같은 브랜치의 앞선 커밋 6개에서 완료
- **`autoExpiryExempt` 필드명 / `is_preserved` 컬럼명 통일** — 컬럼 변경은
  `ddl-auto: update`가 새 컬럼을 만들고 옛 컬럼을 남기므로 데이터 마이그레이션이 필요하다.
  사용자가 별도 작업으로 처리한다.
- **`GlobalExceptionHandler`의 나머지 캐치올 누수** — `HttpMediaTypeNotSupportedException`(415),
  `MethodArgumentTypeMismatchException`(400) 등이 여전히 500이다.
  전면 해결은 `ResponseEntityExceptionHandler` 상속인데 응답이 `ProblemDetail`로 바뀌어
  `{error, message, validation}` 규약이 깨진다. 별도 안건.
- **Invitation의 `accept`·`reject`·`cancel` URL 형태** — 행위자 기준으로 일관돼 있어 유지
- **다른 도메인, 프론트엔드**
