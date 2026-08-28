# Task-104: social API 정돈

## 목적

Social API의 URL 계층과 응답 책임을 정리한다.
기본 네트워크, 라벨 네트워크, 대상 연결 조회를 일관된 경로 아래에 배치하고,
응답에는 클라이언트가 실제로 필요한 정보만 포함한다.

프론트엔드 코드는 이 task의 범위에 포함하지 않는다.

## 목표 계약

### Social

| 메서드 | 목표 URL |
|---|---|
| GET | `/api/v1/social/profiles/{userId}` |
| POST | `/api/v1/traces` |
| GET | `/api/perf/networks/{userId}?circleSize=` |

기존 `/api/v1/social` 접두사는 제거한다.
perf API의 `/v1` 제거는 URL에서만 적용하고 perf 동작은 유지한다.

### Labels

| 메서드 | 목표 URL |
|---|---|
| GET | `/api/v1/labels` |
| GET | `/api/v1/labels?memberId={memberId}` |
| GET | `/api/v1/labels/{labelId}/members` |
| POST | `/api/v1/labels/{labelId}/members` |

라벨 목록은 `id`, `labelName`, `memberCount`를 반환한다.
멤버 목록은 `/members` 하위 자원에서 조회한다.
`memberId` 조회는 해당 멤버가 포함된 라벨을 찾는 역방향 조회다.
멤버 추가는 `201 Created`와 생성된 멤버 자원의 `Location`을 반환한다.

### Network

| 목적 | 목표 URL |
|---|---|
| 기본 네트워크 | `/api/v1/network?circleSize=` |
| 라벨 네트워크 | `/api/v1/network/labels/{labelId}` |
| 대상 연결 엣지 | `/api/v1/network/edges?targetId=&baseNetworkFriendIds=` |
| 추천 | `/api/v1/network/recommendations?anchorId=` |
| 경로 | `/api/v1/network/path?targetId=` |

`/me`, `/mutual/one-hop`, `/mutual/two-hop` 경로는 제거한다.
라벨 네트워크는 별도의 조회 대상이지만 `/network/labels/{labelId}` 아래에 둔다.
라벨 네트워크 요청에는 `circleSize`를 받지 않으며 항상 DUNBAR 기준으로 조회한다.

`/network/edges`는 대상 사용자가 현재 사용자의 친구인지에 따라
직접 친구 연결 또는 2-hop 접점을 반환한다.
2-hop 응답에는 연결 사실만 포함하며 `intimacy`는 `null`이다.

추천 응답은 `id`, `nickname`만 반환한다.
`intimacy`, `mutualCount`, `labelCount`는 응답에 포함하지 않는다.

## 범위 제외

- 프론트엔드 변경
- `/network/path` 응답 필드 변경 — task-105
- Friend Request API 변경 — task-107
