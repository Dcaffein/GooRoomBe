# PLAN — Flag 조회 쿼리에서 상태 어휘 제거

## 도메인 수정 승인 요청

`FlagRepository`는 `flag/domain/flag/repository/`에 위치한 도메인 계층 파일이다.
본 작업은 이 인터페이스의 **메서드 시그니처를 변경**한다 (아래 "변경 파일 목록" 참조).
도메인 엔티티(`Flag`, `FlagSchedule`, `FlagStatus`)의 필드·로직은 변경하지 않는다.

## 작업 목표

영속성 계층이 `FlagStatus` 분류 체계를 자체적으로 판단하는 구조를 제거한다.
DB 쿼리는 스케줄 필드에 대한 비교 술어만 표현하고, 상태 해석의 권위는
`FlagSchedule.calculateStatus()` 하나로 단일화한다.

## 현황 분석

### 1. 상태 규칙이 두 곳에 정의되어 있고 경계가 어긋난다

`FlagStatus`는 저장 컬럼이 아니라 `FlagSchedule.calculateStatus()`가 시각으로부터
파생하는 값이다. 그런데 `FlagJpaRepository`가 같은 규칙을 JPQL로 다시 정의하고 있다.

| 상태 | `FlagSchedule.calculateStatus` | `FlagJpaRepository` JPQL | 일치 |
|------|-------------------------------|--------------------------|------|
| RECRUITING | `now.isBefore(deadline)` | `deadline > :now` | O |
| IN_ACTIVITY | `now.isAfter(start)` | `start <= :now` | **X** |
| ENDED | `now.isAfter(end)` | `end <= :now` | **X** |

`now == startDateTime` 정각에 도메인은 `WAITING`, 쿼리는 `IN_ACTIVITY`로 판단한다.
`endDateTime` 경계도 같은 방식으로 갈린다.

### 2. 네 갈래 중 한 갈래만 사용된다

`FlagRepository.findAllByHostIdsAndStatus`의 호출부는 `FlagQueryService.java:38`
단 한 곳이며, 인자는 항상 `FlagStatus.RECRUITING`이다.

따라서 아래는 전부 죽은 코드다.
- `FlagJpaRepository`의 `findBeforeActivityByHostIds`, `findInProgressByHostIds`, `findEndedByHostIds`
- `FlagRepositoryAdapter.java:91-97`의 `switch` 전체
- `default -> throw` 분기 (`FlagStatus` 4개 값이 모두 `case`에 존재하므로 도달 불가)

### 3. 어댑터가 기준 시각을 자체 결정한다

`FlagRepositoryAdapter.java:90`에서 `LocalDateTime.now()`를 직접 호출한다.
이 때문에 쿼리 결과와 `calculateStatus(now)`를 비교하는 검증이 두 시각의 미세한
차이로 불안정해진다.

### 4. 동치성을 보증하는 테스트가 없다

`flag/adapter/out/persistence/`에는 `FlagInvitationJpaRepositoryTest`만 존재하고
`Flag` 조회 쿼리에 대한 리포지토리 테스트가 없다.

## 변경 파일 목록

| 파일 | 할 일 |
|------|------|
| `flag/domain/flag/repository/FlagRepository.java` | `findAllByHostIdsAndStatus(Set, FlagStatus)` → `findByHostIdsAndDeadlineAfter(Set<Long>, LocalDateTime asOf)`로 교체. `FlagStatus` import 제거 |
| `flag/adapter/out/persistence/FlagRepositoryAdapter.java` | `switch`와 `LocalDateTime.now()` 호출 제거. `asOf`를 그대로 위임. 빈 컬렉션 가드는 유지 |
| `flag/adapter/out/persistence/jpa/FlagJpaRepository.java` | `findRecruitingByHostIds` → `findByHostIdsAndDeadlineAfter`로 리네임. `findBeforeActivityByHostIds`·`findInProgressByHostIds`·`findEndedByHostIds` 3개 삭제 |
| `flag/application/service/flag/FlagQueryService.java` | `getFriendFlags`에서 `LocalDateTime now`를 한 번 확보해 전달 |
| `flag/adapter/out/persistence/FlagJpaRepositoryTest.java` *(신규)* | 동치성 테스트 |

## 구현 방향

### 네이밍 원칙

DB 쿼리 이름에는 **스케줄 필드에 대한 비교 술어만** 남긴다.
`Recruiting`, `BeforeActivity`, `InProgress`, `Ended` 같은 상태 어휘는 도메인 소유다.

```java
List<Flag> findByHostIdsAndDeadlineAfter(Set<Long> hostIds, LocalDateTime asOf);
```

Spring Data 파생 문법은 따르지 않는다. `@Query`가 붙으면 이름 파싱이 일어나지 않으며,
파생 문법을 그대로 지키면 `findAllByHostIdInAndScheduleDeadlineAfter`처럼
임베더블 매핑 구조가 이름으로 노출된다.

### 동치성 근거

`FlagSchedule.validateTimeOrder`가 `deadline <= start < end`를 보장하므로,
`asOf < deadline`이면 `calculateStatus`의 ENDED·IN_ACTIVITY 분기는 반드시 거짓이 되고
RECRUITING에 도달한다. 즉 `deadline > :asOf`는 RECRUITING과 **정확히 동치**이며,
서비스 측 후처리 필터가 필요 없다.

### 기준 시각 호이스팅

`asOf`를 호출자가 넘기도록 바꾼다. 목적은 두 가지다.
- 쿼리와 `calculateStatus`가 같은 시각을 보게 하여 경계 테스트를 결정적으로 만든다
- `getFriendFlags` 한 번의 호출 안에서 시각이 일관된다

## 예상 사이드 이펙트

- **API 응답 변화 없음.** `GET /api/v1/flags/friends`의 결과 집합은 동일하다
  (기존 `findRecruitingByHostIds`와 술어가 같음).
- **`FlagStatus`는 삭제하지 않는다.** `Flag.isRecruiting()`, `unparticipate`,
  `FlagDeletedEvent` 등에서 계속 사용된다. 이번 작업은 리포지토리 계층에서만 걷어낸다.
- **`FlagQueryServiceTest`에는 `getFriendFlags` 테스트가 없다.** 기존 목 스텁 수정이 아니라
  신규 테스트 추가로 처리한다.
- 삭제하는 JPQL 3개는 호출부가 없으므로 컴파일 영향 없음.

## 범위 외 (Out of Scope)

- **`flags.host_id` 인덱스 부재.** `@Table`에 `@Index` 선언이 없고 마이그레이션
  스크립트도 없어 `IN :hostIds` 조회가 현재 풀스캔이다. 개선 여지가 있으나
  DDL 변경이라 별도 작업으로 분리한다.
- **컨트롤러·서비스 교통정리.** API 표면을 건드리므로 별도 브랜치로 분리한다.
- 상태 컬럼 저장(비정규화) 방식으로의 전환. 현 규모에서 정합성 비용이 이득을 넘는다.

## 테스트 전략

`TESTING-GUIDE.md` 기본 프로토콜을 따르되, 신규 리포지토리 테스트 한 건을 추가한다.

**동치성 테스트** — 고정 시각 `asOf`를 기준으로 경계값 flag들을 심고
(`deadline` 직전 / 정각 / 직후), 아래 두 집합이 같은지 검증한다.

- `findByHostIdsAndDeadlineAfter(hostIds, asOf)`의 결과
- 같은 flag들 중 `schedule.calculateStatus(asOf) == RECRUITING`인 것

이 테스트는 누군가 술어를 수정해 도메인 규칙과 갈라지는 순간 깨진다.

**실행 범위:** `FlagJpaRepositoryTest`(신규), `FlagQueryServiceTest`.
전체 스위트는 돌리지 않는다.
