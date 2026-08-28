# Task-108: social network 노출 정책 캡슐화

## 목적

Social Network 조회 서비스에 흩어진 연결 노출 정책을 도메인 정책으로 캡슐화한다.
친밀도에 따른 직접 친구 엣지 수와 2-hop 접점 노출 quota를 서비스 구현과 분리한다.

## 범위

- 친밀도 기반 직접 친구 엣지 limit 계산
- 2-hop 접점 엣지 노출 quota
- 노출 정책을 표현하는 domain policy 추가
- `SocialNetworkQueryService`가 정책에 위임하도록 변경
- 정책 및 서비스 분기 테스트 보강

## 결정사항

- 직접 친구 엣지 limit은 기본 limit과 친밀도 배수를 정책이 결정한다.
- 2-hop quota도 개인정보 노출 정책의 일부로 취급한다.
- API URL, 응답 DTO, Cypher 파라미터명은 변경하지 않는다.
- `PRUNING_EDGE_MIN`, `PRUNING_EDGE_RANGE`는 그래프 조회 알고리즘의 튜닝값이므로 이 task에서 다루지 않는다.
- 정책은 Neo4j나 특정 adapter를 알지 않는다.

## 범위 제외

- 네트워크 조회 결과의 필드 변경
- `/network` API 계약 변경
- 그래프 pruning 알고리즘 변경
