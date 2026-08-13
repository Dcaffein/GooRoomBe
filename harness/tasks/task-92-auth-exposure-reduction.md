# Task-92: 인증 노출면 축소

두 건을 묶는다. 규모가 각각 작고 성격이 같다 — **동작은 지금도 정상인데 필요 이상으로 넓게 열려 있는 것**을 좁히는 심층 방어 작업이다. 기능 변경이 아니다.

---

## 1. `refresh_token` 쿠키가 모든 요청에 동봉된다

### 현황

`AuthCookieManager:47`이 두 쿠키를 모두 `path("/")`로 발급한다.

```java
ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .path("/")          // access / refresh 공통
        .maxAge(maxAge)
        .sameSite("Lax");
```

`refresh_token`의 실제 사용처는 `AccountController`의 두 곳뿐이다.

| 엔드포인트 | 용도 |
|---|---|
| `PATCH /api/auth/tokens` | 재발급 |
| `DELETE /api/auth/tokens` | 로그아웃 |

그런데 `Path=/`라서 **모든 API 요청에 refresh token이 실려 나간다.** 버즈 조회에도, 이미지 업로드에도
7일짜리 토큰이 동봉된다. 로그·프록시·에러 리포팅 등 노출 지점이 요청 수만큼 늘어난다.

### 구현 방향

`refresh_token`만 `Path=/api/auth/tokens`로 좁힌다. `access_token`은 `Path=/` 유지 —
모든 요청에 필요하고, Path는 쿠키별 속성이라 서로 간섭하지 않는다.

### 주의

**① 발급과 만료의 `Path`가 반드시 일치해야 한다.**
브라우저는 **이름 + 도메인 + Path**로 동일 쿠키를 판정한다. `createExpiredCookie`(`AuthCookieManager:59`)도
같은 Path로 고쳐야 하며, 안 고치면 **로그아웃이 쿠키를 지우지 못한다.** 화면상 로그아웃은 되는데
`refresh_token`이 브라우저에 남아 있는 상태가 된다.

**② 배포 시 전환 처리가 필요하다.**
이미 브라우저에 `Path=/`로 심긴 쿠키가 남아 있다. 새 코드가 `Path=/api/auth/tokens`로 만료 쿠키를
보내도 구 쿠키는 지워지지 않고, 이름이 같은 쿠키 두 개가 공존해 어느 쪽이 전송될지 불확실해진다.
**배포 직후 한 번은 두 Path 모두에 만료 쿠키를 보내는 처리**가 있어야 한다.

**③ `DELETE`와 `PATCH`가 같은 경로라 Path 하나로 둘 다 커버된다.** 별도 처리 불필요.

---

## 2. `/api/dev/**`가 전 프로필에서 `permitAll`

### 현황

`SecurityConfig:49`의 `permitAll` 목록에 `/api/dev/**`가 들어 있다. **프로필 조건이 없다.**

현재 이 경로를 쓰는 컨트롤러는 넷이고 전부 `@Profile("local")`이다.

| 컨트롤러 | 경로 |
|---|---|
| `DevController` | `/api/dev` |
| `TraceDevController` | `/api/dev/traces` |
| `NotificationDevController` | `/api/dev/notifications` |
| `FlagSeedController` | `/api/dev/flags` |

즉 **운영에서는 빈 껍데기 규칙**이다. 지금 뚫려 있는 건 아니다.

### 문제

방어가 한 겹뿐이고, 그 한 겹이 **다른 파일에 흩어져 있다.** 누군가 `/api/dev` 아래에
`@Profile`을 빠뜨린 컨트롤러를 하나 추가하는 순간 운영에서 인증 없이 열린다.
`DevController`는 유저를 생성하고 `FlagSeedController`는 데이터를 시딩한다 — 열렸을 때의 피해가 크다.

### 구현 방향

두 안 중 택일이 필요하다.

| 안 | 내용 | 특징 |
|---|---|---|
| A | `permitAll` 목록에서 `/api/dev/**` 제거 | 가장 단순. 로컬에서 인증 없이 dev API를 못 쓰게 되므로 시딩 절차가 불편해질 수 있다 |
| B | `@Profile("local")`인 별도 `SecurityConfig`로 이동 | `PerfSecurityConfig`(`@Profile("perf")`)라는 **선례가 이미 있다.** 로컬 편의를 유지하면서 운영에서 규칙 자체가 사라진다 |

**B를 권장한다.** 기존 패턴과 일치하고 로컬 워크플로가 바뀌지 않는다.

### 주의

`PerfSecurityConfig`와의 관계를 확인할 것. 두 config가 동시에 활성화되는 프로필 조합에서
`@Order`나 `SecurityFilterChain` 중복 등록 문제가 없는지 봐야 한다.

---

## 검증

- **1번**: 로그인 → 아무 API 호출 시 요청 헤더에 `refresh_token`이 **없는지**.
  `PATCH /api/auth/tokens` 호출 시에는 **있는지**. 로그아웃 후 브라우저에 쿠키가 남지 않는지(주의 ①)
- **2번**: `local` 프로필에서 dev API가 인증 없이 동작하는지. `prod` 프로필로 띄웠을 때
  `/api/dev/**`가 **401**을 반환하는지(현재는 404가 난다 — 컨트롤러가 없어서다. 규칙 제거 후엔 401이어야 한다)

## 관련

- `AuthCookieManager:47,59` — 발급/만료 Path
- `SecurityConfig:43-61` — permitAll 목록
- `PerfSecurityConfig` — 프로필별 SecurityConfig 분리 선례

## Result

_미착수_
