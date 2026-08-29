# PLAN — Friend Request 수신 상태 조회 완화

## 1. 작업 목표

`received` 친구 요청 조회에서 `PENDING`과 `HIDDEN`만 허용하던 예외 검증을 제거한다. `sent`의 status 금지는 유지해, 보낸 사람이 수신자의 숨김 처리를 탐색할 수 없게 한다.

## 2. 현황 분석

- `FriendRequestQueryService`는 `received` 요청에서 status가 생략되면 `PENDING`을 기본값으로 사용한다.
- 현재는 `PENDING`, `HIDDEN` 외 상태를 전달하면 `FriendRequestInvalidException`을 던져 400을 반환한다.
- 수락된 요청은 수락 흐름에서 삭제되므로 `received&status=ACCEPTED`는 제한을 제거하면 빈 목록을 반환한다.
- `sent`는 status를 받지 않고 저장소에서 `requesterId + PENDING`만 조회한다. status 파라미터 금지는 보낸 사람이 `HIDDEN` 상태를 탐색하지 못하게 하는 개인정보 규칙이므로 유지한다.

## 3. 변경 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/com/example/DunbarHorizon/social/application/service/FriendRequestQueryService.java` | `received` status whitelist 예외를 제거하고 전달된 enum 상태로 수신 요청을 조회한다. sent 분기와 기본 PENDING 정책은 유지한다. |
| `src/test/java/com/example/DunbarHorizon/social/application/FriendRequestQueryServiceTest.java` | `received + ACCEPTED`가 예외 없이 receiver/status 조건으로 조회되는지 검증한다. 기존 예외 테스트는 변경한다. |

## 4. 구현 방향

- `queryStatus`는 계속 `status == null ? PENDING : status`로 결정한다.
- `direction == SENT`일 때의 status 거부와 `findSentRequests()`의 `PENDING` 고정 조건은 변경하지 않는다.
- `RECEIVED`는 `queryStatus`를 그대로 `findAllByReceiver_IdAndStatus`에 전달한다.
- API의 잘못된 enum 문자열은 기존 Spring binding 오류 처리에 맡긴다.

## 5. 예상 사이드 이펙트

- `GET /api/v1/friend-requests?direction=received&status=ACCEPTED`의 결과가 400에서 200 빈 배열(현재 데이터 모델 기준)로 변경된다.
- 숨김 요청 탐색 방지 정책은 `sent` 분기와 PENDING 고정 저장소 조회로 그대로 보존된다.
- API URL, request/response DTO, domain 상태 전이 규칙은 변경하지 않는다.

## 6. 테스트 전략

사용자가 코드 완료를 확인한 뒤 승인하면 다음 테스트를 실행한다.

```powershell
$env:JAVA_HOME='C:\\Users\\TFX5470H\\.jdks\\corretto-17.0.15'
$env:Path="$env:JAVA_HOME\\bin;$env:Path"
.\\gradlew.bat test --no-daemon --rerun-tasks --tests '*FriendRequestQueryServiceTest'
```

수신 `PENDING` 기본값, `HIDDEN` 조회, `ACCEPTED` 전달 조회, sent status 거부 및 sent PENDING 고정 조회를 검증한다.
