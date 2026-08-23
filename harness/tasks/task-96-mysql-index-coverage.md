# Task-96: Flyway 도입과 MySQL 인덱스 커버리지

> **Domain Change:** [x] — 엔티티 4개에서 `@Table`의 `indexes` 속성을 제거한다.
> 인덱스는 마이그레이션 파일에만 선언한다. (§3)
>
> **작업 단위:** 한 브랜치, 하나의 PLAN, 커밋만 Phase로 분리. Phase 2는 Phase 1의
> 마이그레이션 체계 위에서만 성립하므로 순서는 고정이다.

## Background

스키마는 JPA 어노테이션 + `ddl-auto: update`(`application.yml:37`)로 만들어지고,
테스트는 `ddl-auto: create`로 매번 새로 만든다. `ddl-auto: update`는 **추가만 한다** —
이름 변경, 정의 변경, 삭제가 전부 불가능하다.

그 결과가 실물로 남아 있다.

- 2026-08-22 flag 배포에서 운영 DB에 DDL 세 줄을 손으로 쳤다. 저장소에 기록이 없다.
- `notification_settings` 테이블이 고아로 남아 있다. `NotificationSetting` 엔티티는
  커밋 `579e421`(task-79)에서 삭제됐다.
- `flags.description`이 `mediumtext`다. 엔티티는 최초 커밋부터 `TEXT`였다.

테스트는 이 중 무엇도 잡지 못한다. 엔티티에서 스키마를 새로 만들기 때문이다.

---

## 사전 확인 결과 (2026-08-23, 완료)

읽기 전용 계정으로 운영(Aiven MySQL 8.0.45, `defaultdb`)과 dev를 대조했다.

- **운영 = dev.** 컬럼·인덱스·테이블 콜레이션이 13개 테이블 전부 일치한다.
- 2026-08-22 DDL 3줄은 양쪽 다 적용돼 있다. **dev를 맞추는 선행 작업은 필요 없다.**
- baseline 덤프를 빈 DB에 재생한 결과가 dev 스키마와 차이 0건.

---

## Phase 1 — Flyway 도입

### 1-1. 의존성

```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-mysql'   // MySQL 8은 별도 모듈이 필요하다
```

버전은 Spring Boot 3.4.0이 관리한다.

### 1-2. `V1__baseline.sql`

```bash
mysqldump --no-data --no-tablespaces --skip-lock-tables \
          --skip-add-drop-table --skip-comments \
          --set-gtid-purged=OFF --default-character-set=utf8mb4 \
          -u <읽기전용유저> --host <aiven-host> --port <port> defaultdb \
  | sed -e 's/ AUTO_INCREMENT=[0-9]*//' > V1__baseline.sql
```

**플래그 셋은 선택이 아니다:**
- `--no-tablespaces` — `avnadmin`에 `PROCESS` 권한이 없다
- `--skip-lock-tables` — `--no-data`여도 mysqldump는 기본으로 `LOCK TABLES`를 건다
- `--set-gtid-purged=OFF` — 없으면 복원 시 `SUPER`를 요구하는 구문이 섞인다

**baseline은 있는 그대로 뜬다.** 고아 객체를 빼고 뜨면 운영(기록만)과 새 환경(실행)이
갈라진다. 정리는 §1-7이 별도 버전으로 한다.

### 1-3. `V2__flag_refactor_ddl.sql`을 만들지 않는다

flag 리팩터링 DDL 세 줄은 운영에 이미 적용됐고 baseline 덤프에 들어 있다.
다시 돌리면 없는 컬럼을 `DROP`하려다 실패한다.

### 1-4. 설정 전환

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}   # update → validate
```

### 1-5. 테스트 스키마도 Flyway로

`application-test.yml`의 `ddl-auto: create`를 `validate`로 바꾸고 Flyway를 켠다.
Testcontainers는 `reuse=true`이므로 **전환 직후 한 번은 컨테이너를 지우고** 빈 DB에서
`V1`부터 재생되는지 확인한다.

### 1-6. 불변 규칙

- **적용된 마이그레이션 파일은 수정하지 않는다.** 체크섬이 기록되어 있다.
  잘못 썼으면 다음 번호로 forward-fix.
- **옛 파일을 지우지 않는다.** 빈 DB는 `V1`부터 전부 재생한다.
- **운영 DB에 직접 `ALTER TABLE`을 치지 않는다.**
- **boolean 컬럼 DDL은 `BIT(1)`로 쓴다.** Hibernate가 MySQL에서 Java `boolean`을
  `bit(1)`로 만든다. `BOOLEAN`(=`tinyint(1)`)으로 쓰면 `validate`가 깨진다.

### 1-7. `V2__drop_orphan_notification_settings.sql`

```sql
DROP TABLE IF EXISTS notification_settings;
```

**같이 지우지 않을 것:**
- `refresh_tokens.idx_rt_value_unique` — `UNIQUE KEY`이고
  `RefreshToken.tokenValue`의 `unique = true`를 구현하는 실체다
- Hibernate 자동 생성 이름 인덱스 (`UK6dotkott...`, `UK3shgoehod...`,
  `IDXhsff1gimywjd62i0buehihmsc`) — 정상 동작 중이다

### `flags.description` — 손대지 않는다 (2026-08-23 확인)

엔티티는 `columnDefinition = "TEXT"`, 실물은 `mediumtext`다. baseline 재생 DB에
`JPA_DDL_AUTO=validate`로 부팅한 결과 **통과했다.** `columnDefinition`은 DDL 생성용
지시자라 `validate`가 참조하지 않는다.

스키마도 애노테이션도 바꾸지 않는다. 이 작업 이후 스키마의 정본은
`V1__baseline.sql`이고, 엔티티에 타입을 다시 적는 것은 정본을 둘로 만드는 일이다.

### Phase 1 검증

**§1-1~1-6 적용 전, `JPA_DDL_AUTO=validate`만 켠 부팅으로 이미 확인했다 (2026-08-23):
baseline 재생 DB에서 엔티티 검증 통과, 앱 정상 기동.** 파일 변경 없이 환경변수만으로
재현할 수 있다.

1. 로컬 빈 DB에서 `V1` 재생 후 부팅 성공 — `validate` 통과
2. 운영 스키마 복제본에서 `V1`이 `type = BASELINE`으로 기록만 되는지 확인
3. `./gradlew test` — Testcontainers 재생성 후 전체 통과
4. **dev에 먼저 배포해 부팅을 확인한 뒤 운영에 올린다**

---

## Phase 2 — 인덱스 (`V3__add_flag_indexes.sql`)

같은 브랜치에서 Phase 1 커밋 뒤에 이어서 작업한다.
**우선순위 1·2·3을 한 파일에 전부 넣는다.**

### 2-1. 우선순위 1 — `flag_participants`

`countByFlagIdIn`이 목록 조회 네 경로에서 전부 호출되는데 `flag_id`에 인덱스가 없다.

| 인덱스 | 근거 쿼리 |
|---|---|
| `idx_flag_participants_flag_participant` `(flag_id, participant_id)` | `existsByFlagIdAndParticipantId`, `findByFlagIdAndParticipantId`, `countByFlagId`, `countByFlagIdIn`, `findAllByFlagId`, `findAllParticipantIdsByFlagId`(커버링), `deleteAllByFlagIdsIn` |
| `idx_flag_participants_participant_flag` `(participant_id, flag_id)` | `findFlagIdsByParticipantId`(커버링) |

### 2-2. 우선순위 2 — 배치 쿼리

범위를 좁히는 조건이 없어 `flags` 전체를 훑는다. 행이 쌓이는 만큼 선형으로 느려진다.

| 인덱스 | 근거 쿼리 |
|---|---|
| `idx_flags_end_date_time` `(end_date_time)` | `expireAllExceedingThreshold` |
| `idx_flags_deleted_at` `(deleted_at)` | `findIdsByDeletedAtBefore` (네이티브) |

`auto_expiry_exempt`는 복합에 붙이지 않는다 — `end_date_time < ?`가 range라 뒤 컬럼이
범위를 좁히지 못하고, boolean이라 카디널리티가 2다.

### 2-3. 우선순위 3

| 테이블 | 인덱스 | 근거 쿼리 |
|---|---|---|
| `flags` | `idx_flags_host_deadline` `(host_id, deadline)` | `findByHostIdsAndDeadlineAfter`, `findAllByHostId`(prefix) |
| `flag_invitations` | `idx_flag_invitations_invitee_created` `(invitee_id, created_at)` | `findAllByInviteeIdOrderByCreatedAtDesc` — 정렬까지 인덱스로 |
| `flag_invitations` | `idx_flag_invitations_inviter_created` `(inviter_id, created_at)` | `findAllByInviterIdOrderByCreatedAtDesc` |
| `flag_invitations` | `idx_flag_invitations_flag_invitee` `(flag_id, invitee_id)` | `existsByFlagIdAndInviteeId`, `findInviteeIdsByFlagId`(커버링), `hardDeleteByFlagIdsIn`(prefix) |
| `flag_comments` | `idx_flag_comments_flag_id` `(flag_id)` | `countByFlagId`, `findAllByFlagId`, `hardDeleteByFlagIdsIn` |
| `flag_comments` | `idx_flag_comments_parent_id` `(parent_id)` | `DELETE WHERE c.id = :id OR c.parentId = :id` |
| `flag_memorials` | `idx_flag_memorials_flag_id` `(flag_id)` | `countByFlagId`, `existsByFlagId`, `findAllByFlagId`, `hardDeleteByFlagIdsIn` |

**`flag_invitations.status`를 참조하지 않는다.** 컬럼이 삭제됐다.

### 2-4. 네이밍

마이그레이션은 물리 컬럼명을 쓰므로 **전부 snake_case**, 인덱스 이름은
`idx_{table}_{columns}`로 **반드시 명시한다.**

### Phase 2 검증

**성능 검증은 지금 불가능하다.** 운영도 더미 데이터뿐이라(`flags` 47 ·
`flag_participants` 2) 수백 행 규모에서는 옵티마이저가 인덱스가 있어도 풀스캔을
고른다. 검증 기준을 정합성으로 둔다.

1. `V3`이 빈 DB와 운영 복제본 양쪽에서 에러 없이 적용
2. `SHOW INDEX`로 의도한 컬럼 순서 확인
3. `./gradlew test` 전체 통과
4. `EXPLAIN`은 뜨되 판정에 쓰지 않는다. 데이터가 쌓인 뒤 비교할 기록으로 남긴다

---

## 3. 인덱스 선언을 마이그레이션으로 일원화

`ddl-auto: validate`에서 `@Table(indexes = ...)`는 아무 DDL도 만들지 않는다.
남겨두면 인덱스 선언이 엔티티와 마이그레이션 두 곳으로 갈라진다.

**엔티티 4개에서 `indexes` 속성만 제거한다.** `@Table(name = ...)`은 남긴다.

| 클래스 | 도메인 |
|---|---|
| `account/domain/Auth.java` | account |
| `account/domain/RefreshToken.java` | account |
| `account/domain/outbox/UserEventOutbox.java` | account |
| `notification/domain/DeviceToken.java` | notification |

미사용이 된 `@Index` import도 정리한다.
인덱스 실물은 baseline에 있으므로 **어느 환경에서도 사라지지 않는다.**

**제거하지 않을 것:**
- `columnDefinition`, `length`, `nullable`, `unique` — 인덱스와 무관하고 일부는
  도메인 사실을 서술한다
- `Buzz`·`Notification`의 `@Indexed` — MongoDB용이라 Flyway와 무관하다

## Out of Scope

- 인덱스의 성능 효과 측정
- 콜레이션 혼재(`utf8mb4_unicode_ci` / `utf8mb4_0900_ai_ci`) 정리 — 운영·dev 동일하고
  baseline이 현 상태를 보존한다
- 기존 인덱스의 이름·정의 정리 — baseline에 있는 그대로 둔다
- **flag 외 도메인의 인덱스 추가** — 이후 별도 리팩터링에서 마이그레이션으로 추가한다
- 인덱스 외의 스키마 애노테이션(`columnDefinition` 등) 정리 — 전수 조사 후 별도 task
- 쿼리 재작성·튜닝, Neo4j·MongoDB 인덱스
- 무중단 배포를 위한 expand/contract 분할
