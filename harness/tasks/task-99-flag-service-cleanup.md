# Task-99: Flag 서비스 계층 정리

> **Domain Change:** [ ] — 엔티티·도메인 서비스를 건드리지 않는다.
> 포트는 죽는 메서드 하나 삭제와 리네임 하나만 있다.

## Objective

flag 애플리케이션 계층(서비스 12, 리스너 6)에서 다음을 정리한다.

1. **잠금 순서 결함 1건** — 정원 변경이 잠금 밖에서 센 참여자 수를 쓴다
2. **과다 쿼리 1건** — `getMemorials`가 같은 정보를 세 번 물어본다
3. **거짓 분기 1건** — `invite()`의 `.orElse("")`가 도달할 수 없다
4. **잉여 코드 1건** — `occurredAt`을 두 곳에서 불필요하게 채운다
5. **패키지·이름 불일치 2건** — Invitation 서비스 위치, `FlagManagementService` 이름
6. **어휘 분산 1건** — 배치 두 경로가 자기를 부르는 말이 셋씩이다
7. **테스트 공백 1건** — `FlagPurgeService`

## Background

### 잠금 밖에서 읽은 값

`FlagManagementService.modifyFlagCapacity`가 `countParticipants`를 먼저 부르고
`findByIdForUpdate`로 잠근다. 같은 Flag 행을 잠그는 `FlagParticipationManager.participate`는
순서가 반대다. 두 경로가 경합하면 정원보다 참여자가 많아질 수 있다.

저장소 작업(task-98)에서 `participateByInvitation`을 같은 이유로 교정했다.
이 코드베이스에 "잠금 전에 읽고 잠금 후에 판단"하는 패턴이 두 군데 있었다.

### 서비스 패키지가 도메인과 어긋난다

도메인은 `comment`·`flag`·`invitation`·`memorial` 네 폴더인데
서비스는 `invitation`이 없어 셋이다. Invitation 서비스 둘이 `flag/`에 섞여 있다.

### 배치 어휘

```
FlagLabelingScheduler.runLabeling()  → FlagExpiryService.labelExpiredFlags()   → soft delete
FlagSweepingScheduler.runSweeping()  → FlagHardPurgeService.sweepExpiredData() → hard delete
```

한 경로가 자기를 부르는 말이 셋씩이고, 어느 것도 "삭제"라고 말하지 않는다.
`labelExpiredFlags`는 라벨을 붙이는 것처럼 들리지만 `deleted_at`을 찍는다.

## 의사결정

### QueryService는 유지한다

컨트롤러에서 `FlagQueryController`를 해체한 것과 같은 논리를 적용하지 않는다. 그때는
CQRS 축이 URL에 흔적을 남기지 않아 `/api/v1/flags`를 두 클래스가 나눠 갖고도 담당을
짚을 수 없었다. 서비스는 다르다.

- **분해 축이 포트 이름에 드러난다.** `FlagCommentQueryUseCase` / `FlagCommentCommandUseCase`가
  따로 있고 컨트롤러가 필요한 쪽을 주입받는다.
- **클래스 레벨 트랜잭션 속성이 실제로 다르다.** QueryService 넷은 전부
  `@Transactional(readOnly = true)`다. 이름뿐인 구분이 아니다.

task-96 PLAN에 적은 "컨트롤러와 서비스의 분해 축이 같을 필요는 없다"의 근거가 이것이다.

### flag 명령 서비스 셋은 합치지 않는다

`FlagHostService`(생성) · `FlagModificationService`(변경) · `FlagParticipationService`(참여)는
CQRS가 아니라 축이 셋이다. Participation은 참여·탈퇴를, Modification은 참여가 아닌
Flag 자체를 다룬다. 하나로 합치면 컨트롤러에서 없앤 CQRS 축을 서비스에 되살리는 셈이 된다.

### `FlagManagementService` → `FlagModificationService`

서비스 37개 중 유일한 `*ManagementService`다. 프로젝트의 다른 서비스는 전부 주제 + 동작으로
이름 짓는다 — `FriendshipDecayService`, `FriendshipArchiveService`,
`UserOutboxCleanupService`, `FriendRequestReceiverActionService`.
`Manager` 접미사는 도메인 협력자(`FlagInvitationManager`)와 인프라(`AuthCookieManager`)에만 쓰인다.

포트 `FlagManagementUseCase`도 함께 바꾼다. 서비스만 바꾸면 새 불일치가 생긴다.

### 배치·시드 서비스는 `flag/`에 남긴다

`FlagExpiryService`·`FlagPurgeService`·`FlagSeedService`는 Flag 애그리거트를 다룬다.
별도 폴더로 빼면 도메인에 없는 분류를 서비스 계층에만 만들게 된다.

### `invite()`의 두 번째 Flag 조회는 남긴다

`FlagInvitationManager.invite`가 이미 검증한 Flag를 서비스가 다시 조회한다. 같은 트랜잭션이라
1차 캐시 히트여서 SQL이 나가지 않는다. 없애려면 매니저의 반환 타입을 바꿔야 하는데
얻는 것이 없다. `.orElse("")`를 `orElseThrow`로 바꿔 거짓 분기만 없앤다.

### 테스트는 `FlagPurgeService`만 채운다

테스트 없는 클래스가 넷이지만, task-98에서 `@Transactional`을 떼어낸 곳이라 호출 계약을
고정해둘 값이 있는 것은 이 하나다. 나머지 세 리스너
(`FlagMeetingChangedEventListener`·`FlagMemorialEventListener`·`FlagEncoreEventListener`)는
위임 한 줄짜리다.

### 기존 브랜치에 이어 쌓는다

`ai/refactor-flag-controller-url-ownership`에 URL 6 + 도메인 5 + 저장소 8 + docs 1 커밋이
있고 미병합이다. task-97·98과 같은 이유로 이어서 쌓는다.

## 알려진 제약

### 동작이 바뀌는 지점

**1번뿐이다.** 정원 변경과 참여가 경합할 때만 결과가 달라진다.
정상 흐름에서는 차이가 없다.

나머지는 전부 리네임·이동·잉여 제거이며 API 표면과 응답이 무변경이다.

### 리네임이 넓게 번진다

`FlagModificationService`와 배치 어휘 통일은 포트·컨트롤러·스케줄러·테스트를 함께 건드린다.
순수 리네임이지만 커밋 하나가 여러 파일을 스치므로, 다른 항목과 섞지 않고 커밋 4에 모은다.

### 같은 파일을 두 커밋이 건드린다

`FlagManagementService`는 커밋 1(잠금 순서)과 커밋 4(리네임)에서 모두 바뀐다.
순서대로 진행하면 충돌이 없다.

### `existsByFlagIdAndWriterId`가 죽는다

2번 적용 시 유일한 호출부가 사라져 포트·어댑터·JPA에서 함께 지운다.
task-98에서 추가한 `FlagMemorialJpaRepositoryTest`의 해당 테스트도 제거한다.

## Out of Scope

- **QueryService 제거** — 위 의사결정 참조
- **flag 명령 서비스 통합** — 위 의사결정 참조
- **포트 네이밍 스타일 통일, `FlagRepository` 분할** — task-98에서 범위 외로 둔 그대로
- **인덱스** — task-96
- **다른 도메인의 서비스 계층**
- **프론트엔드** — API 표면이 무변경이라 영향 없음
