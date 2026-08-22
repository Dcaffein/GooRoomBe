# PLAN — FlagInvitation 수명 관리 (task-101)

## 작업 목표

만료된 초대를 두 축에서 정리한다. **같은 브랜치에서 함께 배포하고 커밋만 분리한다.**

1. **Phase 1** — 모집 중이 아닌 플래그의 초대를 조회 응답에서 제외한다.
2. **Phase 2** — 종료된 플래그의 초대 행을 만료 스윕에서 삭제한다.

스키마 변경 없음. `FlagInvitation` 엔티티 무변경. 도메인 수정 승인 요청 없음.

## 현황 분석

### Phase 1이 고치는 것

`FlagInvitationQueryService`의 필터는 `flagMap.containsKey(...)` 하나뿐이다(`:41`, `:57`).
`flagMap`은 `findAllByIdIn()`이고 `Flag`에 `@SQLRestriction("deleted_at IS NULL")`이
걸려 있어 **소프트 삭제된 것만** 빠진다. 마감·종료 여부는 보지 않는다.

| 시점 | 목록 | 수락 |
|---|---|---|
| deadline 경과 ~ 종료 (WAITING) | 노출 | 409 |
| 종료 ~ +24h | 노출 | 409 |
| 종료 +24h, `autoExpiryExempt` | **영구 노출** | **영구 409** |

DTO에 상태·마감 필드가 없어 FE가 걸러낼 수단도 없다. **이 버그는 현재 운영에 살아 있다.**

### Phase 2가 고치는 것

퍼지는 이미 초대를 지운다(`FlagMaintenanceAdapter:68`). 누수는 한 곳뿐이다 —
`expireAllExceedingThreshold`의 `AND f.autoExpiryExempt = false`(`FlagJpaRepository:37`)
때문에 후기·앵코르가 달린 플래그는 `deleted_at`이 영원히 `null`이고, 퍼지는
`deleted_at < :bufferTime`으로 대상을 찾으므로 **퍼지 대상이 된 적이 없다.**

## 변경 파일 목록

### 커밋 1 — Phase 1

| 파일 | 할 일 |
|---|---|
| `application/service/invitation/FlagInvitationQueryService.java` | `getReceived`·`getSent` 필터에 `flag.isRecruiting()` 추가. 공통 술어를 private 헬퍼로 추출 |
| `application/service/invitation/FlagInvitationQueryServiceTest.java` | WAITING·IN_ACTIVITY·ENDED 제외 케이스, RECRUITING 혼합 케이스 (받은/보낸 각각) |
| `adapter/in/web/FlagInvitationControllerTest.java` | 만료 초대가 목록에 안 나오는 통합 케이스 1건 |

### 커밋 2 — Phase 2

| 파일 | 할 일 |
|---|---|
| `adapter/out/persistence/jpa/FlagInvitationJpaRepository.java` | `hardDeleteByFlagEndDateTimeBefore(threshold)` 추가 |
| `application/port/out/FlagMaintenancePort.java` | `purgeInvitationsOfEndedFlags(LocalDateTime threshold)` 추가 |
| `adapter/out/persistence/FlagMaintenanceAdapter.java` | 위임 구현 (이미 `invitationJpaRepository`를 주입받고 있다) |
| `application/service/flag/FlagExpiryService.java` | `FlagMaintenancePort` 주입, 스윕에 단계 추가, 로그에 건수 반영 |
| `application/service/flag/FlagExpiryServiceTest.java` | 호출 계약 + **호출 순서** 검증 |
| `adapter/out/persistence/FlagInvitationJpaRepositoryTest.java` | 쿼리 동작 검증 (exempt 포함 / WAITING 제외 / 소프트 삭제 제외) |

## 구현 방향

### 1. 필터 술어는 `isRecruiting()`이다

`Flag.participate()`의 가드와 같은 술어다. 이걸로 거르면
**"목록에 보이는 것 = 누르면 되는 것"이 정확히 일치한다.**
`!isEnded()`로 거르면 WAITING이 살아남아 보이는데 409 나는 항목이 남는다.

보낸 목록에도 같이 적용한다. DTO에 상태 필드가 없어 만료된 것과 살아 있는 것이
동일하게 렌더되기 때문이다. 숨겨도 깨지는 흐름은 없다 — 중복 방어는
`existsByFlagIdAndInviteeId`가 DB에서 하고, 모집 중이 아닌 플래그로의 초대는
`FlagInvitationManager.invite()`가 통째로 막는다.

**쿼리로 내리지 않는다.** RECRUITING 판정이 `FlagSchedule.calculateStatus`와
리포지토리로 갈라지고, "쿼리 이름엔 필드 비교 술어만" 원칙에도 어긋난다.

### 2. 삭제 트리거는 ENDED다 — deadline이 아니다

`reschedule()`의 가드가 `isBeforeActivity()`(RECRUITING + WAITING)이므로
**WAITING 플래그는 호스트가 일정을 밀면 RECRUITING으로 복귀한다.**
deadline 기준으로 지우면 되살아난 플래그의 초대가 복구 불가능하게 사라진다.
ENDED는 `validateNotEnded()` + `isBeforeActivity()`가 이중으로 막아 일방통행이다.

### 3. `FlagMaintenancePort`에 붙인다

새 스케줄러를 만들지 않는다. `FlagExpiryService.expireEndedFlags()`가 이미
6시간마다 돌고 이미 `threshold`(`now - 24h`)를 계산한다. 같은 값을 재사용한다.

벌크 삭제는 이미 `FlagMaintenancePort` 소관이고 `FlagMaintenanceAdapter`가
`FlagInvitationJpaRepository`를 이미 주입받고 있다. 도메인 리포지토리
(`FlagInvitationRepository`)가 아니라 이쪽에 붙인다.

`expireAllExceedingThreshold`와 달리 **`autoExpiryExempt`로 거르지 않는다.**
exempt를 무시하는 것이 이 Phase의 존재 이유다.

### 4. 실행 순서 — 초대 삭제가 먼저다

```java
int purged  = maintenancePort.purgeInvitationsOfEndedFlags(threshold);  // 먼저
int expired = flagRepository.expireAllExceedingThreshold(threshold, now);
```

`Flag`의 `@SQLRestriction`이 삭제 쿼리의 서브쿼리에도 적용되므로,
소프트 삭제를 먼저 하면 방금 삭제된 플래그가 서브쿼리에서 빠진다.
초대를 먼저 지우면 **exempt와 비-exempt를 한 번에 균일하게 처리**한다.
비-exempt 쪽은 어차피 퍼지도 지우므로 중복이 무해하다.

이전 실행에서 이미 소프트 삭제된 플래그는 서브쿼리에서 빠지고 퍼지가 담당한다.
**의도된 동작이므로 쿼리에 주석으로 남긴다** (`FlagJpaRepository:45`에 선례 있음).

### 5. 배치는 넣지 않는다

최초 1회 실행 시 누적분이 한 번에 지워진다. 현재 규모에서 문제되지 않으므로
단일 statement로 두고 **최초 실행 로그의 건수만 확인한다.**

## 예상 사이드 이펙트

- **목록에서 항목이 줄어든다.** FE 수정은 필요 없지만 배포 직후 초대 개수가
  감소하는 것은 정상이다. FE에 사전 공유 필요.
- **알림 metadata의 `invitationId`가 dangling이 될 수 있다.** 이미 만료된 초대를
  가리키던 알림이므로, 탭했을 때 409 대신 404(`FlagInvitationNotFoundException`)가 나간다.
- **앵코르 자동 초대는 영향받지 않는다.** `FlagEncoreInvitationListener`는 부모의
  *참여자* 목록을 읽고 초대는 앵코르 플래그 자기 것만 조회한다(`:40`).
- **인덱스가 없어 `flags` 풀스캔이다.** task-96이 미착수라
  `idx_flags_end_date_time`이 없다. 다만 바로 옆 `expireAllExceedingThreshold`가
  이미 같은 조건으로 풀스캔 중이고 6시간 주기 백그라운드라 새로 생기는 문제가 아니다.
- 운영 DB의 `flag_invitations.expires_at`·`status` 컬럼은 그대로 둔다.
  엔티티에 대응 필드가 없어 무해하며, 제거는 task-96 소관이다.

## 테스트 전략

기본 프로토콜을 따른다. 두 가지만 명시한다.

- **기존 `FlagInvitationQueryServiceTest`는 안 깨진다.** `buildFlag()`가 deadline을
  `NOW.plusHours(1)`로 잡아 RECRUITING이기 때문이다. **통과가 곧 검증이 아니므로**
  제외 케이스를 반드시 추가한다.
- **`FlagExpiryServiceTest`에서 호출 순서를 검증한다** (`InOrder`).
  4절의 순서가 뒤집히면 exempt 플래그만 처리되는 조용한 회귀가 된다.

실행 범위: `*FlagInvitation*`, `*FlagExpiry*`.
