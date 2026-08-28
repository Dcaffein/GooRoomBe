# PLAN — 연결 중개인 범위 축소 (task-105) 구현 방법

브랜치: `ai/feat-connection-path-scope` (main에서 분기), 커밋 1개

## 현황

| 위치 | 현재 |
|---|---|
| `SocialConnectionPathRepositoryAdapter:24-38` | Cypher에 `LIMIT` 없음. `mid`별 한 행씩 전부 반환 |
| `:51` | `.all()` |
| `ConnectionPathResult` | `(direct, intermediaries)`, `IntermediaryResult(userId, nickname, score)` |
| `SocialConnectionPathQueryService` | 상수 없음. 리포지토리 결과를 그대로 통과시킨다 |
| 테스트 | 0건. `BaseControllerTest:81`의 목 선언이 전부 |

`score` 소비처는 어댑터와 DTO뿐이고 `/path`에는 `@Cacheable`이 없다.

## 변경 파일

### 1. `ConnectionPathResult.java` (dto/result)

```java
public record ConnectionPathResult(
        boolean direct,
        int totalCount,
        List<IntermediaryResult> intermediaries
) {
    public record IntermediaryResult(Long userId, String nickname) {}

    /** 리포지토리가 상한 적용 목록과 전체 수를 함께 반환하기 위한 타입 */
    public record Intermediaries(List<IntermediaryResult> items, int totalCount) {}
}
```

필드 선언 순서가 JSON 순서다. `Intermediaries`는 `/path` 응답 조립에만 쓰이고
포트 아웃이 이미 `ConnectionPathResult.IntermediaryResult`를 반환하고 있어 같은 자리에 중첩한다.

### 2. `SocialConnectionPathRepository.java` (port out)

```java
ConnectionPathResult.Intermediaries findIntermediaries(Long myId, Long targetId, int limit);
```

`SocialNetworkRepository.getNetworkContactsOfTwoHop(..., int strangerQuota)`과 같은 형태로
상한을 파라미터로 받는다.

### 3. `SocialConnectionPathRepositoryAdapter.java`

MATCH·WHERE·`score` 계산은 그대로 두고 마지막 `RETURN` 한 줄을 두 줄로 바꾼다.

```cypher
WITH mid, sqrt(f1.#{INTIMACY} * f2.#{INTIMACY}) AS score
ORDER BY score DESC
WITH collect({userId: mid.#{ID}, nickname: mid.#{NICK}}) AS ranked
RETURN ranked[0..$limit] AS intermediaries, size(ranked) AS totalCount
```

- `ORDER BY`가 `collect` 앞이라 정렬 순서 그대로 리스트에 담긴다
- `size(ranked)`는 자르기 전에 센다. 쿼리는 한 번으로 끝난다
- `score`는 `collect` 맵에 넣지 않는다
- 변수명을 `all`로 쓰지 않는다. Cypher 내장 술어 `all()`과 겹친다

매핑은 행 하나를 받는 형태로 바뀐다.

```java
.mappedBy((ts, r) -> new ConnectionPathResult.Intermediaries(
        r.get("intermediaries").asList(v -> new ConnectionPathResult.IntermediaryResult(
                v.get("userId").asLong(), v.get("nickname").asString())),
        r.get("totalCount").asInt()))
.one().orElse(new ConnectionPathResult.Intermediaries(List.of(), 0));
```

`collect`는 그룹핑 키 없는 집계라 매치가 0건이어도 빈 리스트를 담은 행 하나가 나오지만
`.orElse`로 받아둔다.

### 4. `SocialConnectionPathQueryService.java`

```java
private static final int INTERMEDIARY_LIMIT = 3;
```

`SocialNetworkQueryService.STRANGER_QUOTA`, `SocialExpansionQueryService.REC_MAX_LIMIT`과 같은 자리다.
`direct` 여부와 무관하게 2-hop을 도는 현재 흐름은 유지하고, 자기 자신 대상이면
`new ConnectionPathResult(false, 0, List.of())`.

## 테스트

| 파일 | 상태 | 검증 |
|---|---|---|
| `social/adapter/out/SocialConnectionPathRepositoryAdapterTest` | 신규 | 상한·정렬·집계·프라이버시 |
| `social/application/service/SocialConnectionPathQueryServiceTest` | 신규 | `direct = true`에서도 2-hop 실행, 자기 자신, 상수 전달 |
| `social/adapter/in/web/SocialQueryControllerTest` | 추가 | 응답 본문에 `score` 없음, `totalCount` 있음 |

리포지토리 테스트는 `SocialExpansionRepositoryAdapterTest`와 같이 `@Neo4jRepositoryTest` +
`@Import(어댑터)`로 짠다. 픽스처는 나(1)-중개인 5명-타겟(99), 중개인마다 intimacy를 달리 줘
순서를 만들고 한 명은 `r4.isRoutable = false`로 둔다.

- 공통 친구 3명 이하 → 전부 반환, `totalCount`가 그 수와 같다
- 공통 친구 5명 → `items` 3개, `totalCount` 5
- `items`가 score 내림차순 상위 3명이다
- `isRoutable = false`인 중개인 제외
- 공통 친구 없음 → 빈 리스트, `totalCount` 0

실행: `./gradlew test --tests '*ConnectionPath*' --tests '*SocialQueryControllerTest'`

## 커밋

```
1. feat(social): 연결 중개인을 상위 3명과 전체 수로 좁힌다
```
