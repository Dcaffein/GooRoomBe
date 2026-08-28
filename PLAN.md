# PLAN — task-107 friend-request API 재설계

## 1. 조회 API 통합

- `GET /api/v1/friend-requests?direction=&status=`로 통합한다.
- `direction`을 `FriendRequestDirection`으로 변환하고 조회 in-port를 단일 메서드로 합친다.
- `received`는 기본 `PENDING`, 명시값은 `PENDING`과 `HIDDEN`만 허용한다.
- `sent`는 status를 받지 않고 `PENDING` 요청만 반환한다.
- 기존 `/sent`, `/hidden` 조회 경로를 제거한다.

## 2. Action API 통합

- `PATCH /api/v1/friend-requests/{counterpartId}`에서 상태를 변경한다.
- `DELETE /api/v1/friend-requests/{counterpartId}`에서 보낸 요청을 취소한다.
- service에서 현재 사용자와 상대 사용자 ID로 composite request ID를 생성한다.
- 생성 응답의 `Location`도 counterpartId 기반 경로로 맞춘다.
- 기존 `/{requestId}/accept`, `/{requestId}/hide`, `DELETE /{requestId}/hide`를 제거한다.

## 3. 상태 변경 캡슐화

- `FriendRequest`는 `updateStatus(userId, targetStatus)`와 `cancel(userId)`만 제공한다.
- 상태 변경은 `targetStatus.update(request, userId)`에 위임한다.
- 각 `FriendRequestStatus`가 허용되는 진입 상태와 수신자 권한을 검증한다.
- 취소는 현재 상태의 `cancel`에 위임하고 `PENDING`만 신청자 권한을 검증한다.
- 허용되지 않는 상태 변경과 취소는 공통 예외 helper로 거절한다.
- ACCEPTED 전환 후 Friendship 생성, 요청 삭제, 기존 이벤트 발행을 유지한다.

## 4. 검증

- Controller: 통합 조회, 세 상태 PATCH, 상태 누락 400, counterpartId DELETE
- Service: composite ID 생성, 상태 저장, 수락 후 Friendship·이벤트 처리
- Domain: 허용 전이, 금지 전이, 권한, PENDING 취소

관련 테스트:

```bash
./gradlew test --tests '*FriendRequestControllerTest' \
  --tests '*FriendRequestRequesterActionServiceTest' \
  --tests '*FriendRequestReceiverActionServiceTest' \
  --tests '*FriendRequestQueryServiceTest' \
  --tests '*FriendRequestTest'
```

`PLAN.md`, task 문서, `AGENTS.md`는 코드 커밋에서 제외한다.
