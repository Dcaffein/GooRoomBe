# PLAN — flag 컨트롤러 URL 소유권 정리

## 목표

컨트롤러 분해 축을 **하위 리소스**로 통일하여 URL과 클래스가 1:1로 대응하게 만든다.
커밋 1개. API 표면 변화는 초대 생성 엔드포인트 하나뿐이다.

## 현황

| 컨트롤러 | 축 | `@RequestMapping` | 엔드포인트 |
|---|---|---|---|
| `FlagController` | 리소스(명령) | `/api/v1/flags` | 10 |
| `FlagQueryController` | **CQRS(조회)** | `/api/v1/flags` | 5 |
| `FlagMemorialController` | 하위 리소스 | `/api/v1/flags` | 5 |
| `FlagCommentController` | 하위 리소스 | **`/api/v1`** | 6 |
| `FlagInvitationController` | 하위 리소스 | `/api/v1/flag-invitations` | 5 |

**1. 분해 축이 두 개 섞여 있다.** CQRS 축은 URL에 흔적이 남지 않아 `/api/v1/flags`
하나를 두 클래스가 나눠 갖는 상태를 재생산한다.

**2. `FlagCommentController.java:18`의 `@RequestMapping("/api/v1")`** 이 flag과 무관한
URL 공간까지 클래스 스코프로 선점한다. 메서드에 전체 경로가 있어 동작은 정상이나
클래스 선언만으로 담당 범위를 알 수 없다.

**3. 초대 리소스가 두 클래스에 쪼개져 있다.** 생성만 `FlagController.java:127`의
`POST /api/v1/flags/{flagId}/invitations`, 나머지(수락·거절·취소·조회)는
`FlagInvitationController`의 `/api/v1/flag-invitations`. 루트가 둘인 것 자체가 아니라
"생성만 중첩"이라는 분할에 규칙이 없는 것이 문제다.

**4. `FlagInvitationControllerTest`가 없다.** `invite()`·`updateInvitePermission()`도
컨트롤러 테스트가 없다 (`src/test/.../flag/adapter/in/web/`에 `invitations` 문자열 0건).

## 변경 파일

| 파일 | 할 일 |
|------|------|
| `FlagInvitationController.java` | `invite()` 이관받아 `POST /api/v1/flag-invitations`로 매핑(`@PathVariable flagId` 제거). 클래스 레벨 `@RequestMapping`은 유지 |
| `FlagController.java` | `invite()` 제거. `FlagQueryController`의 5개 흡수 → `FlagQueryUseCase` 의존 추가 |
| `FlagQueryController.java` | **삭제** |
| `FlagCommentController.java` | 클래스 레벨 `@RequestMapping` 제거, 메서드에 전체 경로 명시 |
| `FlagInviteRequest.java` | `flagId` 추가 → `(@NotNull Long flagId, @NotNull Long inviteeId)` |
| `FlagQueryControllerTest.java` | `FlagControllerTest`로 병합 후 삭제 |
| `FlagControllerTest.java` | 흡수한 조회 테스트 5건 추가 |
| `FlagInvitationControllerTest.java` | **신규.** 기존 5개 + 이관받은 `invite()` |

`FlagMemorialController`는 변경 없다 (이미 하위 리소스 축).
`FlagInvitationUseCase.invite(Long flagId, Long inviterId, Long inviteeId)` 시그니처는
그대로이므로 서비스·도메인 계층은 무변경이다.

### 완료 시점

| 컨트롤러 | 담당 URL |
|---|---|
| `FlagController` | `/api/v1/flags` — 생성·수정·삭제·참여자·**조회** |
| `FlagCommentController` | `/api/v1/flags/{flagId}/comments`, `/api/v1/comments/{id}` |
| `FlagMemorialController` | `/api/v1/flags/{flagId}/memorials`, `/api/v1/flags/memorials/{id}` |
| `FlagInvitationController` | `/api/v1/flag-invitations` **하나만** |

## 설계 판단

### 초대는 평평한 루트로 모은다

반대 방향(전부 `/flags/{flagId}/invitations` 아래로)은 성립하지 않는다.

- `GET /received`·`/sent`는 flagId를 받지 않는다(`FlagInvitationController:22-34`,
  리포지토리도 `inviteeId`/`inviterId`만으로 조회). 중첩하면 "그 플래그에서 받은 초대"로
  의미가 바뀐다
- `invitationId`가 전역 유일하므로 경로의 flagId는 중복 정보다. 무시하면 잘못된 flagId가
  통과하고, 검증하면 소득 없는 코드가 는다

중첩 루트를 없애도 잃는 것이 없다. 거기 걸린 엔드포인트는 생성 하나뿐이고, 이미
`inviteeId`를 본문으로 받으므로 flagId만 본문으로 옮기면 된다. 플래그 단위 초대 조회는
HTTP로 노출돼 있지 않다 (`findPendingInviteeIdsByFlagId`는 `FlagEncoreInvitationListener:40`의
중복 초대 필터링용). 나중에 필요해지면 `GET /api/v1/flag-invitations?flagId=...`로 받는다.

### 클래스 레벨 `@RequestMapping`

한 클래스가 두 개 이상의 URL 루트를 담당하면 쓰지 않는다. 공통 접두사가 없는데 붙이면
`/api/v1` 같은 과도하게 넓은 선언이 나온다. 이번에 해당하는 것은 `FlagCommentController`
하나뿐이다. `FlagInvitationController`는 루트가 하나로 모이므로 유지한다.

### `FlagController` 크기

10 → 14개가 된다. 분리 기준이 없는 분리보다 낫다. 크기가 실제로 문제가 되면 다음 축은
참여자 하위 리소스(`/flags/{id}/participants` 3개 → `FlagParticipantController`)이며,
하위 리소스 축과 일관되므로 나중에 떼어내도 규칙이 깨지지 않는다. 이번에는 하지 않는다.

## 파괴 변경

`POST /api/v1/flags/{flagId}/invitations` 삭제 → `POST /api/v1/flag-invitations`(본문에 `flagId`).

- BE 호출부 없음. 앙코르 자동 초대는 `FlagEncoreInvitationListener`가 유스케이스를 직접 호출한다
- FE 영향은 `dunbar-horizon-fe/src/app/actions/flag.ts:119` 한 줄. BE 배포와 어긋나면
  그 사이 초대 생성이 실패한다
- 유예가 필요하면 구 URL을 `@Deprecated`로 한 릴리스 남길 수 있으나 기본은 즉시 제거

## 그 외 영향

- `FlagController` 생성자 의존 4 → 5. `FlagQueryUseCase`가 들어오고
  `FlagInvitationUseCase`는 남는다 — `updateInvitePermission()`(`FlagController.java:116`)이
  계속 쓰며, 이 URL은 참여자 하위 리소스라 `FlagController`에 남는 것이 축에 맞다
- 매핑 충돌 없음. `/flags/{id}`와 `/flags/friends`·`/me`·`/users/...`가 한 클래스에 모이지만
  Spring은 리터럴을 패턴보다 먼저 매칭한다. 이 우선순위 의존이 눈에 보이게 되는 것은 개선이다

## 범위 외

- **하위 리소스 단건 URL 규칙 통일.** `/api/v1/comments/{id}` vs `/api/v1/flags/memorials/{id}`.
  파괴 변경이고 영향 엔드포인트가 5개로 더 넓어 별도 안건
- **`FlagSeedController`.** `@Profile("local")` + `/api/dev/flags`로 URL 공간이 겹치지 않는다
- **social 도메인 컨트롤러.** flag에서 규칙을 확정한 뒤 적용
- **서비스 계층 재편.** 컨트롤러와 서비스의 분해 축이 같을 필요는 없다.
  `FlagCommentCommandService`/`QueryService` 같은 CQRS 쌍은 그대로 둔다
- **패키지 위치 정규화.** `notification/application/NotificationService`,
  `trace/application/TraceService`가 `application/service/` 규칙에서 벗어나 있음

## 테스트

`TESTING-GUIDE.md` 프로토콜을 따른다.

**검증 기준: 컨트롤러 테스트의 요청 URL 문자열이 바뀌는 곳은 초대 생성 한 군데뿐이어야 한다.**
그 외가 바뀌었다면 이관 실수이거나 범위 이탈이다. 클래스 이동과 URL 변경이 한 디프에
섞이므로 리뷰 시 이 기준으로 URL 문자열을 훑는다. `FlagInvitationControllerTest` 신규
작성분은 처음부터 최종 URL 기준으로 쓴다.

추가 검증:
- `flagId` 누락 시 `400` (`@NotNull`)
- 구 URL `POST /api/v1/flags/{flagId}/invitations` 호출 시 `404`

실행 범위: `FlagControllerTest`, `FlagInvitationControllerTest`,
`FlagCommentControllerTest`, `FlagMemorialControllerTest`.

## 브랜치

`ai/refactor-flag-deadline-query` 머지 후의 `main`에서 분기,
`ai/refactor-flag-controller-url-ownership`. 커밋 1개.
