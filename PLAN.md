# PLAN — 최근 플래그 조회를 경로 세그먼트로 분리

## 작업 목표

`GET /api/v1/flags?userId=&sort=recent`를 `GET /api/v1/flags/recent?userId=`로 옮긴다.
컨트롤러 메서드 하나의 매핑과 테스트만 바뀐다. 서비스·도메인·DTO는 무변경이다.

## 문제

`/api/v1/flags`에 두 핸들러가 쿼리 파라미터 조건으로 매달려 있었다.

```java
@GetMapping(params = {"userId", "role"})        getUserFlagsByRole
@GetMapping(params = {"userId", "sort=recent"}) getRecentFlags
```

1. **api-docs가 깨진다.** OpenAPI에는 "이 파라미터가 있을 때만 이 오퍼레이션"이라는
   표현이 없다. springdoc이 둘을 한 오퍼레이션으로 합치면서 `operationId`는
   `getRecentFlags`, `parameters`는 `getUserFlagsByRole`의 것(`role` required)을 물고 갔다.
   존재하지 않는 조합이 문서에 노출됐고, FE가 이걸 보고 구현 불일치로 신고했다.
2. **`sort`가 이름값을 못 한다.** 정렬 옵션이 아니다. `getRecentFlags`는 주최·참여
   합집합을 **5개로 잘라서** 준다(`FlagQueryService:55`). `role=` 쪽은 한 역할을
   제한 없이 준다. 같은 조회의 정렬 변형이 아니라 서로 다른 조회다.

## 결정 — 경로 분리

```java
@GetMapping("/recent")
public ResponseEntity<List<FlagResult>> getRecentFlags(@RequestParam Long userId)
```

이 컨트롤러는 이미 `/me`, `/friends`로 조회 종류를 경로 세그먼트로 구분한다.
`sort=recent`만 혼자 쿼리 파라미터 판별을 썼다. 기존 관례로 맞춘다.

`userId`는 쿼리 파라미터로 남는다 — "경로는 호출자 기준 스코프(`/me`),
쿼리 파라미터는 임의 유저 지정" 규칙은 그대로다. 구 URL
`/flags/users/{userId}/recent`로 되돌아가는 것이 아니다.

**병합안은 기각했다.** 두 핸들러를 `@GetMapping(params = "userId")` 하나로 합치고
`role`을 optional로 받으면 문서는 고쳐지지만 `if (role != null)` 분기가 컨트롤러로
들어온다. 유스케이스 둘을 한 핸들러가 조건 분기로 나눠 갖는 형태가 된다.

`/flags/recent`는 `/flags/{flagId}`와 충돌하지 않는다. `{flagId}`가 `Long`이라
`"recent"`는 바인딩되지 않고, Spring이 리터럴 경로를 우선한다. `/me`·`/friends`가
이미 같은 조건에서 돈다.

## 변경 파일

- `FlagController.java` — `params = {"userId", "sort=recent"}` → `"/recent"`
- `FlagControllerTest.java` — 해당 케이스 URL 수정, 구 쿼리 형태 400 검증 1건 추가

## FE 영향

`src/app/actions/flag.ts:37` 한 줄. FE가 이미 그 줄을 고치는 중이라 추가 비용은 없다.

```ts
`/api/v1/flags/recent?userId=${userId}`
```

## 검증

`./gradlew test --tests "*FlagControllerTest*"` — 23건 통과.
