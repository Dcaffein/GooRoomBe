# PLAN — task-108 Social Network 노출 정책 캡슐화

## 1. 작업 목표

Social Network 엣지 조회에서 직접 친구 연결 수와 2-hop 접점 노출 limit을 결정하는 개인정보 노출 정책을 domain으로 옮긴다. API 계약, 결과 DTO, Neo4j/Cypher와 그래프 pruning 알고리즘은 변경하지 않는다.

## 2. 현황 분석

- `SocialNetworkQueryService`가 직접 친구 여부를 분기한 뒤 `5 + intimacy * 5`로 직접 친구 엣지 limit을 계산하고, 2-hop 연결에는 limit `5`를 전달한다.
- 이 값들은 클라이언트 응답으로 내려가는 연결 수를 결정하는 정책인데 application service의 상수와 구현식으로 흩어져 있다.
- `PRUNING_EDGE_MIN`, `PRUNING_EDGE_RANGE`는 기본/라벨 네트워크 그래프를 조회하는 알고리즘 튜닝값이므로 그대로 서비스에 둔다.
- 기존 서비스 단위 테스트는 직접 친구의 limit `7`과 비친구의 limit `5`를 저장소 호출 인자로 검증하고 있다.

## 3. 변경 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/com/example/DunbarHorizon/social/domain/friend/SocialNetworkExposurePolicy.java` | 직접 친구 친밀도에서 노출 엣지 limit을 계산하고, 2-hop 접점 limit을 제공하는 domain policy를 추가한다. |
| `src/main/java/com/example/DunbarHorizon/social/application/service/SocialNetworkQueryService.java` | 직접 친구 limit 공식과 2-hop limit 상수를 제거하고 policy에 위임한다. pruning 상수와 직접 친구/2-hop 저장소 선택 책임은 유지한다. |
| `src/test/java/com/example/DunbarHorizon/social/domain/friend/SocialNetworkExposurePolicyTest.java` | 기본·중간·최대 친밀도에 대한 직접 친구 limit과 2-hop limit을 단위 테스트한다. |
| `src/test/java/com/example/DunbarHorizon/social/application/service/SocialNetworkQueryServiceTest.java` | policy가 계산한 값이 각 직접 친구/2-hop 저장소 호출에 전달되는지 검증하도록 보강한다. |

## 4. 구현 방향

- 정책은 `directFriendEdgeLimit(double intimacy)`와 `twoHopContactEdgeLimit()`를 제공한다.
- 현재 동작을 보존한다: 친밀도 `0.0 → 5`, `0.5 → 7`, `1.0 → 10`; 2-hop limit은 `5`다. 정수 변환도 현재와 같이 소수점을 버린다.
- 정책 객체는 Spring component로 등록해 application service에 생성자 주입한다. Neo4j 타입이나 repository 의존성은 추가하지 않는다.
- 서비스는 친구 존재 여부를 판단하고 해당 조회 port를 선택하는 orchestration만 담당한다. policy의 숫자 규칙은 서비스에 남기지 않는다.

## 5. 예상 사이드 이펙트

- 정책 값과 Cypher 파라미터명(`dynamicLimit`, `strangerQuota`)은 그대로여서 API 응답과 저장소 쿼리 결과는 변하지 않는다.
- `SocialNetworkQueryService` 생성자 의존성이 하나 추가되므로 Mockito 기반 단위 테스트에서 policy를 mock으로 주입한다.
- 작업 트리에 존재하는 `SocialQueryController` → `SocialNetworkController` 파일명 변경과 untracked task 문서는 본 작업 범위 밖이며 수정하지 않는다.

## 6. 테스트 전략

```powershell
$env:JAVA_HOME='C:\\Users\\TFX5470H\\.jdks\\corretto-17.0.15'
$env:Path="$env:JAVA_HOME\\bin;$env:Path"
.\\gradlew.bat test --no-daemon --rerun-tasks --tests '*SocialNetworkExposurePolicyTest' --tests '*SocialNetworkQueryServiceTest'
```

테스트는 정책의 경계 친밀도와 limit, 서비스의 빈 기준 네트워크 조기 반환, 직접 친구/2-hop 분기별 저장소 호출 인자를 검증한다.
