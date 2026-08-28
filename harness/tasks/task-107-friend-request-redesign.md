# Task-107: friend-request API 재설계

## 목적

친구 요청 API를 관계 상대방과 요청 상태 중심으로 표현한다. 내부 composite request ID는 클라이언트 계약에서 숨기고, 분리된 조회·상태 변경 경로를 하나의 규칙으로 통합한다.

## API 계약

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/v1/friend-requests` | 친구 요청 생성 |
| GET | `/api/v1/friend-requests?direction=&status=` | 보낸·받은 요청 조회 |
| PATCH | `/api/v1/friend-requests/{counterpartId}` | 받은 요청 상태 변경 |
| DELETE | `/api/v1/friend-requests/{counterpartId}` | 보낸 요청 취소 |

기존 `/sent`, `/hidden`, `/{requestId}/accept`, `/{requestId}/hide` 경로는 제거한다.

## 결정사항

- URL은 내부 request ID 대신 상대 사용자 ID를 사용한다.
- 생성 응답의 `Location`도 상대 사용자 ID 기반 경로를 가리킨다.
- `direction`은 필수이며 `received`와 `sent`만 허용한다.
- `received`는 `PENDING`을 기본값으로 사용하고 `PENDING`, `HIDDEN`만 조회한다.
- `sent`는 status를 받지 않고 `PENDING` 요청만 조회한다.
- PATCH는 `ACCEPTED`, `HIDDEN`, `PENDING`을 받는다.
- 허용 전이는 `PENDING → ACCEPTED/HIDDEN`, `HIDDEN → ACCEPTED/PENDING`이다.
- 상태 전이와 권한 검증은 `FriendRequestStatus`가 담당한다.
- 취소는 `PENDING` 상태에서 신청자만 가능하다.
- ACCEPTED 전환 시 Friendship 생성, 요청 삭제, 기존 이벤트 발행을 유지한다.

## 제외 범위

- composite ID 생성 규칙 변경
- HIDDEN 요청의 재전송 정책 변경
- 공통 enum 변환 오류 처리와 예외 응답 체계 정리
- FriendRequest 응답 DTO 구조 변경
