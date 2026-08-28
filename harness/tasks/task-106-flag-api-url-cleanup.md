# Task-106: Flag API URL 정돈

## 목적

Flag 목록의 사용 맥락을 명확히 구분하고, Flag Invitation의 필터와 동사를 자원 경로에서 제거한다.

## 결정 사항

### Flag 조회

| 용도 | API | 응답 |
|---|---|---|
| 내 Flag 목록 | `GET /api/v1/flags?role=&page=&size=` | `Slice<FlagResult>` |
| Flag 피드 | `GET /api/v1/flags/feed?page=&size=` | `Slice<FlagResult>` |
| 프로필용 Flag | `GET /api/v1/flags/profile?userId=` | `List<FlagResult>` |
| 상세 | `GET /api/v1/flags/{flagId}` | `FlagDetailResult` |

- 내 목록은 로그인 사용자를 기준으로 하며 `role`은 필수다.
- 피드는 친구가 주최하고 현재 모집 중인 Flag를 제공한다.
- 프로필 목록은 대상 사용자가 주최하거나 참여한 Flag를 최신순으로 최대 5건 제공하며 페이지네이션하지 않는다.
- `/me`, `/friends`, `/recent`와 루트의 `userId` 조회를 폐기한다.

### Flag Invitation

| 용도 | API |
|---|---|
| 생성 | `POST /api/v1/flag-invitations` |
| 목록 | `GET /api/v1/flag-invitations?direction=` |
| 수락 | `PATCH /api/v1/flag-invitations/{invitationId}` body `{status: ACCEPTED}` |
| 거절·취소 | `DELETE /api/v1/flag-invitations/{invitationId}` |

- `direction`은 `received` 또는 `sent` 필수값이다.
- 목록 응답은 상대 사용자를 `counterpartNickname`으로 통일한다.
- DELETE는 초대받은 사용자의 요청이면 거절, 초대한 사용자의 요청이면 취소한다.
- Flag Invitation은 여러 Flag와 사용자를 잇는 독립 자원이므로 `/flags/{flagId}` 아래에 두지 않는다.
- `/received`, `/sent`, `/{id}/accept`, `/{id}/reject`를 폐기한다.

## 범위 제외

Flag 명령 API와 Comments, Memorials API는 변경하지 않는다.
