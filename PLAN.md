# PLAN — Flyway 도입과 flag 인덱스 (task-96)

## 도메인 수정 승인 요청

**엔티티 4개에서 `@Table`의 `indexes` 속성을 제거한다.** `@Table(name = ...)`은 남긴다.

| 클래스 | 도메인 | 제거 대상 |
|---|---|---|
| `Auth` | account | `idx_auth_user_provider` |
| `RefreshToken` | account | `idx_rt_user_id` |
| `UserEventOutbox` | account | *(이름 없는 선언)* |
| `DeviceToken` | notification | `idx_device_token_user_id` |

`ddl-auto: validate`에서 이 선언은 아무 DDL도 만들지 않는다. 인덱스 실물은 baseline에
들어 있으므로 어느 환경에서도 사라지지 않으며 **기능 변화는 없다.**

**같이 제거하지 않는 것:** `columnDefinition`, `length`, `nullable`, `unique` —
인덱스와 무관하고 일부는 도메인 사실을 서술한다. `Buzz`·`Notification`의 `@Indexed`는
MongoDB용이라 Flyway와 무관하다.

## 작업 목표

스키마 변경 경로를 `ddl-auto: update`에서 Flyway 마이그레이션으로 옮기고, 그 위에서
고아 테이블을 정리하고 flag 도메인 인덱스를 추가한다.
**한 브랜치에서 함께 배포하고 커밋만 셋으로 나눈다.**

## 최종 상태

- `src/main/resources/db/migration/`에 `V1`~`V3` 세 파일이 있다.
- 운영·dev는 `V1`을 baseline으로 **기록만** 하고 `V2`·`V3`을 실행한다.
  빈 DB(로컬·CI·Testcontainers)는 `V1`부터 전부 실행한다.
- `ddl-auto`가 운영·테스트 모두 `validate`다. 엔티티와 스키마가 어긋나면 부팅이 실패한다.
- `notification_settings` 테이블이 모든 환경에서 사라진다.
- flag 도메인 5개 테이블에 인덱스 11개가 있다.
- **인덱스 선언이 마이그레이션 파일에만 존재한다.** 엔티티에는 남지 않는다.

## 커밋 구성

### 커밋 1 — Flyway 도입

| 파일 | 변경 |
|---|---|
| `build.gradle` | `flyway-core`, `flyway-mysql` 추가 |
| `db/migration/V1__baseline.sql` | 신규 — 운영 스키마 덤프 (13 테이블) |
| `application.yml` | `spring.flyway` 블록, `ddl-auto` → `validate` |
| `src/test/resources/application-test.yml` | `ddl-auto: create` → `validate`, Flyway 활성화 |

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
```

### 커밋 2 — 고아 테이블 정리

| 파일 | 변경 |
|---|---|
| `db/migration/V2__drop_orphan_notification_settings.sql` | 신규 |

```sql
DROP TABLE IF EXISTS notification_settings;
```

### 커밋 3 — flag 도메인 인덱스

| 파일 | 변경 |
|---|---|
| `db/migration/V3__add_flag_indexes.sql` | 신규 — 인덱스 11개 |

| 테이블 | 인덱스 |
|---|---|
| `flag_participants` | `(flag_id, participant_id)`, `(participant_id, flag_id)` |
| `flags` | `(end_date_time)`, `(deleted_at)`, `(host_id, deadline)` |
| `flag_invitations` | `(invitee_id, created_at)`, `(inviter_id, created_at)`, `(flag_id, invitee_id)` |
| `flag_comments` | `(flag_id)`, `(parent_id)` |
| `flag_memorials` | `(flag_id)` |

이름은 `idx_{table}_{columns}`로 명시한다. 컬럼 목록과 근거 쿼리는 task-96 §2.

### 커밋 4 — 인덱스 선언을 마이그레이션으로 일원화

| 파일 | 변경 |
|---|---|
| `account/domain/Auth.java` | `@Table`의 `indexes` 속성 제거 |
| `account/domain/RefreshToken.java` | 〃 |
| `account/domain/outbox/UserEventOutbox.java` | 〃 |
| `notification/domain/DeviceToken.java` | 〃 |

`@Index` import도 함께 정리한다. DDL·데이터 변경 없음.

## 배포 순서

**dev에 먼저 올려 부팅을 확인한 뒤 운영에 올린다.** `validate` 전환은 불일치가 있으면
부팅 자체를 실패시킨다.

## 검증

기존 테스트를 새로 쓰지 않는다. 스키마 재생 자체가 검증이다.

1. 로컬 빈 DB에서 `V1`→`V3` 재생 후 부팅 성공 (`validate` 통과)
2. 운영 스키마 복제본에서 `V1`이 `type = BASELINE`으로 기록만 되고 `V2`·`V3`만 적용
3. `SHOW INDEX`로 인덱스 11개의 컬럼 순서 확인
4. `./gradlew test` — Testcontainers 재생성 후 전체 통과

## 범위 밖

- 인덱스의 성능 효과 측정 — 운영도 더미 데이터뿐이라 지금은 성립하지 않는다
- 콜레이션 혼재(`utf8mb4_unicode_ci` / `utf8mb4_0900_ai_ci`) 정리
- 기존 인덱스의 이름·정의 정리
- 쿼리 재작성, Neo4j·MongoDB 인덱스
