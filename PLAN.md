# PLAN — flag 도메인 URL 정리

## 목표

flag 도메인 32개 엔드포인트의 URL 구조를 확정한다. 다른 도메인은 건드리지 않는다.
브랜치 `ai/refactor-flag-controller-url-ownership`, `c6594a4`에 이어서 커밋 4개.

## 규칙

1. URL 경로 하나는 클래스 하나가 담당한다. 분해 축은 하위 리소스.
2. 부모 없이 존재할 수 없는 자원은 최상위 루트를 갖지 않는다.
3. 부모를 가로지르는 조회가 있으면 평평한 루트, 없으면 중첩 루트.
4. 경로는 호출자 기준 스코프, 쿼리 파라미터는 임의 유저 지정.
5. 컬렉션 루트에 DELETE를 걸지 않는다. 대상을 경로에 명시한다.

## 최종 URL 구조

### FlagController — `@RequestMapping("/api/v1/flags")`

```
POST   /api/v1/flags                                        생성 (parentFlagId 있으면 Encore)
GET    /api/v1/flags?userId={userId}&role={FlagRole}        특정 유저의 Flag (역할별)
GET    /api/v1/flags?userId={userId}&sort=recent            특정 유저의 최근 Flag 5개
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

`GET /api/v1/flags`는 `@GetMapping(params = ...)`으로 라우팅한다.
파라미터가 없으면 매칭되는 핸들러가 없다 — 전체 Flag 목록은 존재하지 않는다.

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
POST   /api/v1/flag-invitations                             생성 (본문에 flagId)
GET    /api/v1/flag-invitations/received                    받은 Invitation
GET    /api/v1/flag-invitations/sent                        보낸 Invitation
POST   /api/v1/flag-invitations/{invitationId}/accept       수락 (invitee)
POST   /api/v1/flag-invitations/{invitationId}/reject       거절 (invitee)
DELETE /api/v1/flag-invitations/{invitationId}              철회 (inviter)
```

### FlagSeedController — `@RequestMapping("/api/dev/flags")` · `@Profile("local")`

```
POST   /api/dev/flags/seed
```

## 서비스 계층

Comment·Memorial 중첩으로 경로에 `flagId`가 생겼다. 유스케이스가 받아 소유를 검증하고,
어긋나면 `404`를 던진다. 작성자 검증 앞에 둔다.

```java
// FlagCommentCommandUseCase
Long createRootComment(Long flagId, Long userId, String content, boolean isPrivate);
Long createReply(Long flagId, Long parentId, Long userId, String content, boolean isPrivate);
void updateComment(Long flagId, Long commentId, Long userId, String content, boolean isPrivate);
void deleteComment(Long flagId, Long commentId, Long userId);

// FlagMemorialCommandUseCase
Long createMemorial(Long flagId, Long userId, String content);
void updateMemorial(Long flagId, Long memorialId, Long requesterId, String content);
void deleteMemorial(Long flagId, Long memorialId, Long requesterId);
```

도메인 계층은 무변경이다.

## 변경 파일

| 파일 | 할 일 |
|------|------|
| `FlagController.java` | 유저 축 조회 쿼리화, `/participants/me`, Participant PATCH, `{flagId}` 통일 |
| `FlagCommentController.java` | 클래스 레벨 `@RequestMapping` 중첩 경로로 |
| `FlagMemorialController.java` | 클래스 레벨 `@RequestMapping` 중첩 경로로 |
| `FlagCommentCommandUseCase.java` + 구현 | `flagId` 추가, 소유 검증 |
| `FlagMemorialCommandUseCase.java` + 구현 | `flagId` 추가, 소유 검증 |
| `FlagControllerTest.java` | URL 갱신, Participant PATCH 테스트 신규 |
| `FlagCommentControllerTest.java` | URL 갱신, 소유 불일치 404 |
| `FlagMemorialControllerTest.java` | URL 갱신, 소유 불일치 404 |
| `FlagCommentCommandServiceTest.java` | 소유 검증 케이스 |
| `FlagMemorialCommandServiceTest.java` | 소유 검증 케이스 |

## 커밋 구성

| | 내용 | 파괴 변경 |
|---|---|---|
| 1 | 경로 변수 `{flagId}` 통일 | 0 |
| 2 | 탈퇴 `/participants/me` + Participant PATCH | 2 |
| 3 | Comment·Memorial 중첩 + 소유 검증 | 5 |
| 4 | 유저 축 조회 쿼리 파라미터화 | 2 |

## 테스트

`TESTING-GUIDE.md` 프로토콜을 따른다.

커밋마다 컨트롤러의 `@RequestMapping` + `@*Mapping`을 파싱해 전후 URL 집합을 diff하고,
의도한 것만 바뀌었는지 확인한다.

- `GET /api/v1/flags` 파라미터 없이 → `400`
- `DELETE /api/v1/flags/{flagId}/participants` → `405`
- 소유 불일치(`/flags/{다른flagId}/comments/{commentId}`) → `404`, Memorial 동일
- 구 URL(`/api/v1/comments/{id}`, `/api/v1/flags/memorials/{id}`, `/flags/users/{userId}`) → `404`

실행 범위: `FlagControllerTest`, `FlagCommentControllerTest`, `FlagMemorialControllerTest`,
`FlagInvitationControllerTest`, `FlagCommentCommandServiceTest`, `FlagMemorialCommandServiceTest`.
`GlobalExceptionHandler`를 건드리므로 `*ControllerTest` 전체도 돌린다.

## FE 대응 목록

파괴 변경 9개, 호출부 10곳. 사용자가 직접 처리한다.

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

`FlagComments.tsx:135`, `FlagMemorial.tsx:33`은 `flagId`를 prop으로 갖고 있다.
`c6594a4`의 Invitation 생성 URL(`flag.ts:119`)도 미대응이다.

## 손대지 않는 것

- `GET /api/v1/flags/me`·`/friends`
- `PATCH /flags/{flagId}/schedule/deadline` (모집 마감)
- Invitation의 `accept`·`reject`·`cancel` 형태
- `/api/v1/flag-invitations` 이름
- `POST /api/v1/flags`가 `parentFlagId`로 Encore를 가르는 형태
- 다른 도메인 (account, buzz, social, notification, trace) — 전수 조사에서 8건
- 서비스 계층 재편, 패키지 위치 정규화, 응답 DTO·상태 코드
- 프론트엔드
