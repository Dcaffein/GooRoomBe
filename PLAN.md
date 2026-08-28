# PLAN — task-106 Flag API URL 정돈

## 1. Flag 조회

### Web·UseCase

- `FlagController`의 `/me`, `/friends`, `/recent`, 루트 `userId+role` 매핑을 제거한다.
- 루트 `/flags`는 `@CurrentUserId`, 필수 `role`, `page`, `size`를 받아 `getFlagsByRole`을 호출한다.
- `/flags/feed`는 현재 사용자와 페이지 정보를 받아 `getFeedFlags`를 호출한다.
- `/flags/profile`은 필수 `userId`만 받아 `getProfileFlags`를 호출한다.
- `/flags`와 `/flags/feed`는 `page=0`, `size=20`, `createdAt DESC`로 고정한 `PageRequest`를 사용한다.
- `FlagQueryUseCase` 반환형은 내 목록과 피드만 `Slice<FlagResult>`로 바꾸고, 프로필과 상세는 각각 `List`, 단건을 유지한다.

### Service·Repository

- `getFlagsByRole`은 `HOST`면 hostId, `PARTICIPANT`면 participantId로 Slice 조회한다.
- `getFeedFlags`는 Social 포트에서 친구 ID를 구한 뒤, 친구가 주최하고 조회 시각 기준 모집 중인 Flag를 Slice로 조회한다. 친구가 없으면 저장소를 호출하지 않고 빈 Slice를 반환한다.
- Slice의 현재 페이지에 포함된 Flag ID와 호스트 ID만 모아 사용자 정보와 참여자 수를 일괄 조회하고 Slice 메타데이터를 유지해 `FlagResult`를 만든다.
- `FlagRepository`의 호스트·참여·피드 조회에 `Pageable`을 추가하고 반환형을 `Slice<Flag>`로 변경한다.
- 참여 목록은 참여 Flag ID 전체를 먼저 읽는 현재 흐름을 제거하고, `FlagParticipant` 조건을 포함한 JPQL로 직접 페이지 조회한다.
- 프로필 조회는 호스트 또는 참여자인 Flag를 `createdAt DESC`로 조회하고, 서비스 상수로 최대 5건을 제한한다.
- `findRecentByUserId`는 조회 조건이 드러나는 `findByHostIdOrParticipantId`로 변경하고 결과 보강 로직을 재사용한다.

## 2. Flag Invitation

### 조회

- `FlagInvitationController`의 `/received`, `/sent`를 루트 GET 하나로 합치고 필수 문자열 `direction`을 받는다.
- `FlagInvitationDirection`은 대소문자와 무관하게 `received`, `sent`를 변환하고 그 외 값은 Flag Invitation 예외로 400을 반환한다.
- `FlagInvitationQueryUseCase`를 `getInvitations(userId, direction)` 단일 메서드로 변경한다.
- `FlagInvitationQueryService`는 direction에 따라 invitee 또는 inviter 저장소 조회를 선택하고, 상대 사용자 ID 추출 방식만 다르게 적용한다.
- `ReceivedFlagInvitationResult`, `SentFlagInvitationResult`는 `counterpartNickname`을 가진 `FlagInvitationResult`로 통합한다. 모집이 끝났거나 사용자 정보를 찾지 못한 초대를 제외하는 현재 정책은 유지한다.

### 상태 변경·삭제

- `FlagInvitationStatusUpdateRequest`와 상태 값을 추가하고 `PATCH /{invitationId}`를 상태 변경 흐름에 연결한다. `FlagInvitation`이 `ACCEPTED`만 허용하고 피초대자 권한을 검증한다.
- `FlagInvitationManager`는 상태 변경 후 참여자를 생성하는 교차 도메인 조율을 맡는다.
- `DELETE /{invitationId}`는 서비스가 초대를 조회해 `FlagInvitation.delete()`에 요청자 판단을 위임한 뒤 삭제한다.
- `FlagInvitation.delete()`는 invitee의 요청을 `reject`, inviter의 요청을 `cancel`로 분기하고 제3자에게 `FlagInvitationAccessException`을 발생시킨다.
- 수락 시 참여자 저장 후 초대를 삭제하는 흐름과 초대 생성·알림 흐름은 변경하지 않는다.

## 3. 테스트

- `FlagControllerTest`: 새 세 목록 경로, 현재 사용자 전달, 필수 `role/userId`, 기본 페이지 값을 검증한다.
- `FlagQueryServiceTest`: HOST/PARTICIPANT 분기, 빈 피드의 저장소 미호출, Slice 메타데이터 유지, 프로필 제한 5와 결과 보강을 검증한다.
- `FlagJpaRepositoryTest`: 호스트·참여·피드 페이지 경계, `createdAt DESC`, 모집 마감 및 soft-delete 제외를 검증한다.
- `FlagInvitationControllerTest`: direction별 조회, 누락·오류 direction, 통합 응답 필드, PATCH 수락, DELETE를 검증한다.
- `FlagInvitationQueryServiceTest`: received/sent별 저장소 선택과 상대 사용자 매핑, 모집 종료 Flag 제외를 검증한다.
- `FlagInvitationServiceTest`: 상태 변경 후 참여자 저장·초대 삭제와 삭제 판단의 도메인 위임을 검증한다.
- `FlagInvitationManagerTest`: 수락 후 참여자 생성과 초대 권한 규칙을 검증한다.
- `FlagInvitationTest`: `ACCEPTED` 상태 제약과 invitee 거절, inviter 취소, 제3자 거절을 검증한다.

```bash
./gradlew test --tests '*FlagControllerTest' \
  --tests '*FlagQueryServiceTest' \
  --tests '*FlagJpaRepositoryTest' \
  --tests '*FlagInvitationControllerTest' \
  --tests '*FlagInvitationQueryServiceTest' \
  --tests '*FlagInvitationServiceTest' \
  --tests '*FlagInvitationManagerTest' \
  --tests '*FlagInvitationTest'
```

## 4. 커밋 단위

1. Flag 목록·피드·프로필 조회 URL과 페이지네이션
2. Flag Invitation 조회·상태 변경 URL 통합
3. task-106과 PLAN 문서 정리

각 코드 커밋은 관련 테스트를 통과한 뒤 커밋 직전에 검토를 받는다.
