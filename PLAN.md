# PLAN — flag 도메인 URL 정리

## 목표

flag 도메인 32개 엔드포인트의 URL 구조를 확정한다. 다른 도메인은 건드리지 않는다.
`ai/refactor-flag-controller-url-ownership`의 `c6594a4`에 이어서 쌓는다.

## 규칙

1. **URL 경로 하나는 클래스 하나가 담당한다.** 분해 축은 하위 리소스.
2. **부모 없이 존재할 수 없는 자원은 최상위 루트를 갖지 않는다.**
   Comment는 Flag에도 Buzz에도 달리므로 `/api/v1/comments/{id}`만으로는 어느 도메인인지 알 수 없다.
3. **부모를 가로지르는 조회가 있으면 평평한 루트, 없으면 중첩 루트.**
   Invitation은 `/received`·`/sent`가 모든 Flag를 가로지르므로 평평한 루트.
   Comment·Memorial은 특정 Flag 안에서만 접근하므로 중첩 루트.
4. **경로는 호출자 기준 스코프, 쿼리 파라미터는 임의 유저 지정.**
5. **컬렉션 루트에 DELETE를 걸지 않는다.** 대상을 경로에 명시한다.

## 최종 URL 구조

### FlagController — `@RequestMapping("/api/v1/flags")`

```
POST   /api/v1/flags                                        생성 (parentFlagId 있으면 Encore)
GET    /api/v1/flags?userId={userId}&role={FlagRole}        특정 유저의 Flag (역할별)
GET    /api/v1/flags?userId={userId}&sort=recent            특정 유저의 최근 Flag 5개 (역할 합집합)
GET    /api/v1/flags/me?role={FlagRole}                     내 Flag (역할별)
GET    /api/v1/flags/friends                                친구들의 Flag
GET    /api/v1/flags/{flagId}                               상세
DELETE /api/v1/flags/{flagId}                               종료
PATCH  /api/v1/flags/{flagId}/details                       제목·설명 수정
PATCH  /api/v1/flags/{flagId}/capacity                      정원 수정
PUT    /api/v1/flags/{flagId}/schedule                      일정 교체
PATCH  /api/v1/flags/{flagId}/schedule/deadline             모집 마감
POST   /api/v1/flags/{flagId}/participants                  참여
DELETE /api/v1/flags/{flagId}/participants/me               탈퇴
PATCH  /api/v1/flags/{flagId}/participants/{participantId}  Participant 수정 (canInvite)
```

`GET /api/v1/flags`는 `params` 조합으로 핸들러를 라우팅한다.

```java
@GetMapping(params = {"userId", "role"})
@GetMapping(params = {"userId", "sort=recent"})
```

**파라미터 없이 `GET /api/v1/flags`를 호출하면 매칭되는 핸들러가 없어 404다.**
"전체 Flag 목록"은 존재하지 않으며 이 구조가 그 부재를 유지한다.

### FlagCommentController — `@RequestMapping("/api/v1/flags/{flagId}/comments")`

```
GET    /api/v1/flags/{flagId}/comments                      트리 조회
GET    /api/v1/flags/{flagId}/comments/count                개수
POST   /api/v1/flags/{flagId}/comments                      루트 Comment 작성
POST   /api/v1/flags/{flagId}/comments/{parentId}/replies   답글 작성
PATCH  /api/v1/flags/{flagId}/comments/{commentId}          수정
DELETE /api/v1/flags/{flagId}/comments/{commentId}          삭제
```

### FlagMemorialController — `@RequestMapping("/api/v1/flags/{flagId}/memorials")`

```
POST   /api/v1/flags/{flagId}/memorials                     작성
GET    /api/v1/flags/{flagId}/memorials                     목록
GET    /api/v1/flags/{flagId}/memorials/count               개수
PATCH  /api/v1/flags/{flagId}/memorials/{memorialId}        수정
DELETE /api/v1/flags/{flagId}/memorials/{memorialId}        삭제
```

### FlagInvitationController — `@RequestMapping("/api/v1/flag-invitations")`

```
POST   /api/v1/flag-invitations                             초대 생성 (본문에 flagId)
GET    /api/v1/flag-invitations/received                    받은 Invitation
GET    /api/v1/flag-invitations/sent                        보낸 Invitation
POST   /api/v1/flag-invitations/{invitationId}/accept       수락 (invitee)
POST   /api/v1/flag-invitations/{invitationId}/reject       거절 (invitee)
DELETE /api/v1/flag-invitations/{invitationId}              철회 (inviter)
```

`c6594a4`에서 확정. 이번 작업에서 변경 없다.

### FlagSeedController — `@RequestMapping("/api/dev/flags")` · `@Profile("local")`

```
POST   /api/dev/flags/seed
```

## 설계 근거

### 유저 축 조회는 쿼리 파라미터

`flags/users/{userId}`는 "Flag 아래의 User"로 읽힌다. 실제 의미는 "User X의 Flag"다.

`flags/{hostId}` 형태는 **불가능하다** — `GET /api/v1/flags/{flagId}`와 경로 형태가 같아
`/api/v1/flags/5`가 5번 Flag인지 5번 User의 Flag 목록인지 구분할 수 없다.

쿼리 파라미터로 옮기면 `flags`가 먼저 오고 `/flags/me?role=`과 어휘가 같아진다(규칙 4).
`role`을 생략하면 두 역할의 합집합이며 `sort=recent`가 이 경우다
(`findRecentByUserId`는 host OR participant를 `createdAt DESC`로 5개 반환).

`?hostId=`·`?participantId=`로 나누는 안도 검토했으나 `/flags/me?role=`과 어휘가 어긋나
같은 질의를 두 방식으로 표현하게 되어 뺐다.

### 탈퇴는 `/participants/me`

컬렉션 루트의 DELETE는 관례상 "전원 삭제"로 읽힌다. 실제 동작은
`leaveFlag(flagId, currentUserId)` — 호출자 하나만 빠진다.

`/me`로 명시하면 오독이 사라지고, 나중에 host가 participant를 내보내는 기능이 생길 때
`DELETE /participants/{userId}` 자리가 비어 있게 된다.

### Participant 수정은 리소스 경로에

`FlagParticipant`의 수정 가능한 필드는 `canInvite` 하나뿐이다
(`id`·`flagId`·`participantId`는 불변). 필드 하나 때문에 5단 경로를 파는 대신
Participant 리소스에 PATCH를 건다. 필드가 늘어도 URL이 늘지 않는다.

요청 본문(`FlagInvitePermissionRequest{canInvite}`)과 유스케이스 시그니처
(`updateInvitePermission(flagId, requesterId, participantUserId, canInvite)`)는 그대로다.

### Comment·Memorial은 Flag 아래

`/api/v1/comments/{id}`는 부모 없이 최상위 루트를 점유한다. Comment는 Buzz에도 있어
(`/api/v1/buzzes/{buzzId}/comments/{commentId}`) URL만으로 도메인을 알 수 없다.
`/api/v1/flags/memorials/{id}`는 "id가 `memorials`인 Flag"로 읽힌다.

중첩으로 두 컨트롤러가 클래스 레벨 `@RequestMapping`을 갖게 된다. 현재
`FlagCommentController`는 프로젝트 20개 컨트롤러 중 **유일하게 클래스 레벨 선언이 없다.**

### 경로 변수는 `{flagId}`로 통일

`FlagController` 안에서 `{id}`와 `{flagId}`가 섞여 있다. URL 문자열은 바뀌지 않는다.

## 서비스 계층 — 소유 검증

Comment·Memorial 중첩의 부수 결과다. **이 항목만 서비스 계층을 건드린다.**

경로에 `flagId`가 생겼는데 유스케이스가 받지 않으면
`PATCH /api/v1/flags/999/comments/5`가 5번 Comment가 999번 Flag에 속하지 않아도 성공한다.
URL이 거짓말을 하게 되므로 `flagId`를 넘기고 소유가 어긋나면 `404`를 던진다.
작성자 검증(현재 있음)은 그대로 두고 그 앞에 소유 검증을 더한다.

최종 시그니처:

```java
// FlagCommentCommandUseCase
Long createRootComment(Long flagId, Long userId, String content, boolean isPrivate);   // 무변경
Long createReply(Long flagId, Long parentId, Long userId, String content, boolean isPrivate);
void updateComment(Long flagId, Long commentId, Long userId, String content, boolean isPrivate);
void deleteComment(Long flagId, Long commentId, Long userId);

// FlagMemorialCommandUseCase
Long createMemorial(Long flagId, Long userId, String content);                          // 무변경
void updateMemorial(Long flagId, Long memorialId, Long requesterId, String content);
void deleteMemorial(Long flagId, Long memorialId, Long requesterId);
```

도메인 계층은 무변경이다.

## 유지하는 것

- **`GET /api/v1/flags/me`·`/friends`** — `{flagId}` 자리 리터럴이지만 flag id가 숫자라
  충돌하지 않고 Spring이 리터럴을 먼저 매칭한다. `/me`는 관용이다.
  `GET /api/v1/flags`(전체 목록)가 없어 이 둘이 목록 역할을 하는 구조가 일관돼 있다.
- **`PATCH /flags/{flagId}/schedule/deadline`**(모집 마감) — "deadline을 지금으로 민다"는
  읽기가 성립한다. 본문 없는 PATCH가 흠이지만 URL 문제는 아니다.
- **Invitation의 `accept`·`reject` vs `DELETE`(cancel)** — 갈림이 **행위자 기준**으로
  일관돼 있다. invitee가 응답하는 것과 inviter가 거두는 것은 권한 검증이 다르고
  (`validateInvitee` vs 발신자 검증), accept는 Participant를 생성하는 부수 효과가 있어
  순수한 상태 변경이 아니다. 세 조작 모두 Invitation 행을 삭제하므로
  `PATCH /{id}` + `{status}`로 바꾸면 존재하지 않는 상태 리소스를 있는 것처럼 표현하게 된다.
- **`/api/v1/flag-invitations` 이름** — `/invitations`로 줄일 수 있으나
  `flag-` 접두사가 자기설명적이다.

## 변경 파일

| 파일 | 할 일 |
|------|------|
| `FlagController.java` | 유저 축 조회 쿼리화, `/participants/me`, Participant PATCH, `{flagId}` 통일 |
| `FlagCommentController.java` | `@RequestMapping("/api/v1/flags/{flagId}/comments")` |
| `FlagMemorialController.java` | `@RequestMapping("/api/v1/flags/{flagId}/memorials")` |
| `FlagCommentCommandUseCase.java` + 구현 | 3개 메서드에 `flagId` 추가, 소유 검증 |
| `FlagMemorialCommandUseCase.java` + 구현 | 2개 메서드에 `flagId` 추가, 소유 검증 |
| `FlagControllerTest.java` | URL 갱신 |
| `FlagCommentControllerTest.java` | URL 갱신 + 소유 불일치 404 |
| `FlagMemorialControllerTest.java` | URL 갱신 + 소유 불일치 404 |
| 서비스 테스트 | 소유 검증 케이스 추가 |

`FlagInvitationController`·`FlagSeedController`·도메인 계층은 무변경이다.

## 테스트

`TESTING-GUIDE.md` 프로토콜을 따른다.

커밋마다 **전후 URL 집합을 기계적으로 diff**하여 의도한 것만 바뀌었는지 확인한다
(`@RequestMapping` + `@*Mapping` 파싱). `c6594a4`에서 31개 중 1개만 바뀌었음을
이 방법으로 확인했다.

추가 검증:
- `GET /api/v1/flags` 파라미터 없이 → `404`
- `PATCH /api/v1/flags/{다른flagId}/comments/{commentId}` → `404` (소유 불일치), Memorial 동일
- 구 URL(`/api/v1/comments/{id}`, `/api/v1/flags/memorials/{id}` 등) → `404`

실행 범위: `FlagControllerTest`, `FlagCommentControllerTest`, `FlagMemorialControllerTest`,
`FlagInvitationControllerTest`, 소유 검증을 추가한 서비스 테스트.

## 커밋 구성

| 커밋 | 내용 | 파괴 변경 |
|---|---|---|
| 1 | 경로 변수 `{flagId}` 통일 | 0 |
| 2 | 탈퇴 `/participants/me` + Participant PATCH | 2 |
| 3 | Comment·Memorial 중첩 + 소유 검증 | 5 |
| 4 | 유저 축 조회 쿼리 파라미터화 | 2 |

## FE 대응 목록

파괴 변경 9개. FE 호출부 10곳이며 사용자가 직접 처리한다.

```
GET    /flags/users/{userId}?role=       →  GET /flags?userId={userId}&role=
GET    /flags/users/{userId}/recent      →  GET /flags?userId={userId}&sort=recent
DELETE /flags/{flagId}/participants      →  DELETE /flags/{flagId}/participants/me
PATCH  .../participants/{pId}/invite-permission  →  PATCH .../participants/{pId}
POST   /comments/{parentId}/replies      →  POST /flags/{flagId}/comments/{parentId}/replies
PATCH  /comments/{commentId}             →  PATCH /flags/{flagId}/comments/{commentId}
DELETE /comments/{commentId}             →  DELETE /flags/{flagId}/comments/{commentId}
PATCH  /flags/memorials/{id}             →  PATCH /flags/{flagId}/memorials/{memorialId}
DELETE /flags/memorials/{id}             →  DELETE /flags/{flagId}/memorials/{memorialId}
```

Comment·Memorial 호출 컴포넌트(`FlagComments.tsx:135`, `FlagMemorial.tsx:33`)는
`flagId`를 이미 prop으로 갖고 있어 prop 추가 없이 인자만 넘기면 된다.

`c6594a4`의 Invitation 생성 URL 변경(`flag.ts:119`)도 아직 미대응이다.

## 범위 외

- **다른 도메인**(account, buzz, social, notification, trace).
  전수 조사에서 8건을 찾았으나 flag를 확정한 뒤 별도로 다룬다.
- **Encore 생성의 표현.** `POST /api/v1/flags`가 본문의 `parentFlagId` 유무로
  일반 생성과 Encore를 가른다. 한 엔드포인트가 두 조작을 겸하는 형태지만
  이번 범위에 넣지 않는다. 기록만 남긴다.
- **서비스 계층 재편**, **패키지 위치 정규화**, **응답 DTO·상태 코드.**
- **프론트엔드.**

## 브랜치

`ai/refactor-flag-controller-url-ownership`에 커밋 4개 추가.
