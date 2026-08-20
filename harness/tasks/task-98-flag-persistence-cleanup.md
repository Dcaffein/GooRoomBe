# Task-98: Flag 저장소 계층 정리

> **Domain Change:** [x] — 포트 인터페이스가 도메인 패키지에 있어 수정한다.
> 엔티티·규칙은 무변경이다.

## Objective

flag 저장소 계층(포트 4, 어댑터 5, JPA 리포지토리 5)에서 다음을 정리한다.

1. **무력화된 트랜잭션 분리** — 퍼지 배치가 의도와 달리 단일 트랜잭션으로 동작
2. **자식 수명 불일치** — 참여자만 삭제 규칙에서 벗어나 두 번 지워짐
3. **죽은 포트 메서드 3개**
4. **과다 조회 1건**, **이름 불일치 2건**
5. **테스트 공백 4개** — 특히 퍼지가 실제로 지우는지 검증하는 테스트가 없음

## Background

### 삭제 전략

이 도메인은 **부하 분산**을 위해 소프트 삭제 → 지연 하드 삭제를 쓴다. 사용자 삭제와
자동 만료는 Flag에 `deleted_at`만 기록하고, 무거운 다중 테이블 정리는 널널한 배치로 미룬다.

```
FlagLabelingScheduler   0 0 0/6 * * *   6시간마다   soft delete
FlagSweepingScheduler   0 0 3 * * *     하루 1회    hard delete + 자식 청소
```

`FlagMaintenanceAdapter`가 500건씩 끊어 트랜잭션을 분리하려 하지만,
`TransactionTemplate`의 기본 전파가 `REQUIRED`이고 호출자 `FlagHardPurgeService`가
`@Transactional`을 걸고 있어 **청크마다 바깥 트랜잭션에 합류한다.**

### 참여자만 규칙 밖에 있다

`FlagDeletionEventListener`가 삭제 직후 `deleteAllParticipants`를 호출하고, 3시 배치가
같은 데이터를 다시 지운다. 자동 만료 경로에는 즉시 삭제가 없다 —
`expireAllExceedingThreshold`가 벌크 UPDATE라 `FlagDeletedEvent`가 발행되지 않기 때문이다.
배치만으로 충분하다는 것을 자동 만료 경로가 이미 증명하고 있다.

### 퍼지 처리량이 JPA 인터페이스에 있다

`LIMIT 5000`이 네이티브 쿼리 문자열 안에 박혀 있다. 조정하려면 JPA 인터페이스를 고쳐야 한다.
같은 퍼지 정책인 `bufferTime`(12시간)은 `FlagHardPurgeService`에 있어 소유가 갈려 있다.

## 의사결정

### 삭제 전략 자체는 유지한다

복구 경로가 없어 "소프트 삭제인데 복구가 안 된다"고 볼 여지가 있으나, **복구는 애초에
목표가 아니었다.** 목적이 부하 분산이면 자식을 하드 삭제하는 것도 일관되며,
6시간 주기의 가벼운 UPDATE와 하루 주기의 무거운 정리는 비용에 맞게 배치돼 있다.

12시간 창 동안 데이터가 새지 않는 것도 확인했다 — `FlagCommentQueryService:30`,
`FlagMemorialQueryService:31`이 Flag를 먼저 로드해 404를 내고,
`FlagInvitationQueryService:42,58`이 `flagMap.containsKey(...)`로 걸러낸다.

### 참여자 즉시 삭제는 제거한다

전략을 바꾸는 것이 아니라 규칙에서 벗어난 참여자를 규칙 안으로 들이는 것이다.
중복 삭제가 사라지고 자식 다섯의 수명이 통일된다.

부수적으로 `deleteAllParticipants`가 포트·어댑터·JPA 3계층에서 죽는다.

### 설정 파일로 빼지 않는다 — 검토 후 철회

`CHUNK_SIZE`를 `@Value`로 주입해 테스트에서 낮추는 안을 검토했다가 **철회했다.**
테스트 편의가 프로덕션 설계를 끌고 간 판단이었다. 검증 대상이 청크 산술 5줄인데
`application.yml`에 항목 세 개를 만드는 것은 과하고, 운영에서 이 값을 조정해야 했던 적도 없다.

`batchSize`는 쿼리 파라미터로, 상수는 호출자가 든다. 다른 쿼리 메서드와 같은 형태다.

### 청크 경계 테스트는 생략한다

`static final CHUNK_SIZE`로는 501건을 심어야 하는데, 검증 대상(`i += chunkSize` 루프)에
비해 비용이 크다. `FlagMaintenanceAdapterTest`는 원래 중요한 것 — 다섯 테이블에서
실제로 사라지는지 — 에 집중한다.

### 잠금 어휘는 `findByIdForUpdate`

`findByIdExclusive`와 `findByIdForUpdate`가 같은 `PESSIMISTIC_WRITE`를 다르게 부르고 있다.
SQL `FOR UPDATE`와 직결되어 의미가 명확한 쪽으로 통일한다. 잠금은 도메인이 의도적으로
요청하는 것이므로 포트에 드러나는 것이 맞다.

### `participateByInvitation` 검사 순서를 바꾼다

참여자가 배치까지 남게 되면서, 소프트 삭제된 Flag에 참여 중이면서 대기 초대까지 가진
유저가 그 초대를 수락하면 `404` 대신 `409`가 나간다. 도달하기 매우 어렵고 둘 다 에러
응답이지만, 잠금을 먼저 잡는 것이 동시성상으로도 맞아 함께 교정한다.

### 기존 브랜치에 이어 쌓는다

`ai/refactor-flag-controller-url-ownership`에 URL 6 + 도메인 5 커밋이 있고 미병합이다.
flag 관련 미병합 브랜치는 이것 하나뿐이며, URL 변경 때문에 이미 FE와 배포 조율이 필요하다.
WORKFLOW의 `main` 분기 규칙에서 벗어나는 부분이며 사용자 승인을 받았다.

## 알려진 제약

### 동작이 바뀌는 지점 두 곳

- **퍼지 부분 성공** — 지금은 중간 실패 시 전부 롤백. 변경 후 성공한 청크가 남는다.
  퍼지가 멱등하므로(지워진 id는 다음 실행에서 조회되지 않는다) 안전하다.
- **참여자 수명 연장** — 즉시 삭제에서 배치 삭제로 바뀌어 최대 하루 남는다.

### 알림 재발송 가드가 사라진다

`FlagDeletionEventListener`의 알림 조건이 `!participantIds.isEmpty()`인데, 같은 메서드가
끝에서 그 데이터를 지우고 있어 재실행 시 알림이 중복되지 않았다. 설계된 가드가 아니라
부수 효과다. 현재 이 리스너에 `@Retryable`이 없고 Spring이 `@TransactionalEventListener`를
자동 재전달하지 않으므로 위험은 없다. **재시도를 붙이거나 아웃박스로 옮기면
명시적 멱등 키가 필요하다.**

### 처리량 천장

소프트 삭제 6시간 주기, 하드 삭제 하루 1회 최대 5000건.
하루 소프트 삭제가 5000건을 넘으면 잔여분이 누적된다. 현재 규모에선 무관하다.

### 신규 테스트 4개가 모두 Testcontainers를 요구한다

이번 작업에서 실행 시간이 가장 긴 부분이다.

## Out of Scope

- **포트 네이밍 스타일 통일** — `FlagRepository`에 도메인 어휘(`isParticipating`)와
  JPA 파생 이름(`findAllByHostId`)이 섞여 있다. 통일 방향에 판단이 갈리고 호출부가 넓다.
- **`FlagRepository` 분할** — 메서드 21개(Flag 11 + Participant 10)로 크지만,
  `FlagParticipant`의 생성자가 package-private이고 `Flag.participate()`만 생성하므로
  Flag 애그리거트 내부다. 애그리거트 루트당 리포지토리 하나가 맞다.
- **인덱스** — task-96
- **다른 도메인의 저장소 계층**
- **프론트엔드** — API 표면이 무변경이라 영향 없음
