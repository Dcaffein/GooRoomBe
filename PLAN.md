# PLAN — task-89: 토큰 재발급 실패 응답 정규화

> 태스크 문서: `harness/tasks/task-89-fix-token-reissue-error-response.md`
> 브랜치: `ai/fix-token-reissue-error-response`

---

## 작업 목표

`PATCH /api/auth/tokens`의 모든 실패 경로를 500 → 의미에 맞는 4xx로 정규화한다.
동시에 access token 경로(필터)와 refresh token 경로(재발급)가 **같은 예외 타입과 같은 `error`
값**을 내도록 변환 지점을 하나로 모은다.

---

## 현황 분석

### 1. 재발급의 모든 실패가 500이다

`LoginService.reissue:82`가 JWT 파싱을 가장 먼저 수행하는데, 여기서 나오는 예외가 전부
`BusinessException`이 아니라 jjwt 예외 / `IllegalArgumentException`이다. `GlobalExceptionHandler`에
전용 핸들러가 없어 catch-all `@ExceptionHandler(Exception.class)`(`:107`)로 떨어진다.

| 상황 | 실제 예외 | 현재 응답 | 빈도 |
|---|---|---|---|
| `refresh_token` 쿠키 없음 (`required=false` → null) | `IllegalArgumentException` | 500 | 비로그인 상태 호출마다 |
| refresh token 만료 | `ExpiredJwtException` | **500** | **전 사용자, 7일마다** |
| 서명 위조/변조 | `security.SignatureException` | 500 | 공격 시 |
| 유효하지만 DB에 없음 | `TokenTheftDetectedException` | 403 | 정상 |

프론트는 401 단일 분기로 재로그인을 유도하므로, 500은 그 분기를 비껴가 **사용자가 재로그인
화면에 도달하지 못한다.** 재발급 API의 존재 이유인 "만료 처리"가 정확히 막혀 있다.

### 2. 인증 실패 응답의 출구가 둘이고 서로 도달할 수 없다

| | `JwtAuthenticationEntryPoint` | `GlobalExceptionHandler` |
|---|---|---|
| 소속 | Security 필터 체인 | Spring MVC (`@RestControllerAdvice`) |
| 담당 | access token | refresh token |

`@RestControllerAdvice`는 `DispatcherServlet` 내부 예외만 본다. 필터는 그 앞단이라 구조적으로
도달 불가. 반대로 EntryPoint는 MVC 예외를 볼 수 없다.

`PATCH /api/auth/tokens`는 `SecurityConfig:56`에서 `permitAll`이므로 access token 상태와 무관하게
컨트롤러까지 도달한다. 즉 두 경로를 가르는 것은 access token 유무가 아니라 **예외 발생 위치**다.

### 3. `SecurityException` 분기가 죽어 있다

jjwt 0.11.5 jar 확인 결과, root 패키지에 `SignatureException`은 있으나 **`SecurityException`은
없다.** 따라서 `import io.jsonwebtoken.*` 하에서도 `JwtTokenProvider:67`의
`catch (SecurityException ...)`와 `JwtAuthenticationEntryPoint:41`의 `instanceof SecurityException`은
`java.lang.SecurityException`으로 해석되어 **절대 걸리지 않는다.** 컴파일은 통과한다.

결과: 위조 토큰이 provider에서 로그도 없이 통과하고, EntryPoint에서도 분기 실패해
"유효하지 않은 토큰 형식입니다" 대신 기본 문구가 나간다.

### 4. 두 출구의 `error` 어휘가 다르다

EntryPoint는 `"TokenExpiredException"` / `"InvalidTokenException"`을 **하드코딩**하고,
GlobalExceptionHandler는 `e.getClass().getSimpleName()`을 쓴다. 어순만 뒤집힌 별개 문자열이라
프론트가 두 경로를 같은 조건으로 분기할 수 없다.

### 5. 죽은 코드

- `LoginService:93-95` — DB `expiryDate`는 JWT `exp`에서 뽑은 값이므로 DB 기준 만료면 `:82`에서
  먼저 터진다. `ExpiredRefreshTokenException`은 한 번도 발생하지 않는다.
- `RefreshToken.isExpired():40` — 호출부 0. `LocalDateTime.now()`(KST)와 UTC `expiryDate`를
  비교해 9시간 어긋난다.
- `account/domain/exception/`의 `ExpiredTokenException`, `InvalidJwtException`,
  `RefreshTokenNotFoundException` — `src/main` 어디에서도 `new` 되지 않는다.

### 6. jjwt 참조 범위

현재 `src/main`에서 `io.jsonwebtoken`을 import하는 파일은 `JwtTokenProvider`,
`JwtAuthenticationEntryPoint` 둘뿐이다. `validateToken` 호출자도 `JwtAuthenticationFilter`와
`JwtAuthTokenAdapter` 둘뿐이다. → 변환 지점을 provider 하나로 모으는 것이 가능하다.

---

## 구현 방향

### 핵심: 변환 지점을 `JwtTokenProvider` 하나로

두 출구가 **같은 예외 타입**을 보게 만들어 일관성을 구조적으로 보장한다. 그 공통 타입은 jjwt가
아니라 `BusinessException`을 상속한 우리 예외여야 한다.

```java
// JwtTokenProvider — jjwt 타입이 이 클래스 밖으로 나가지 않는다
public AuthPrincipal validateToken(String token) {
    try {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return new AuthPrincipal(Long.parseLong(claims.getSubject()), claims.get("role", String.class));
    } catch (ExpiredJwtException e) {
        throw new ExpiredTokenException();
    } catch (JwtException | IllegalArgumentException e) {
        throw new InvalidTokenException();
    }
}
```

`JwtException`으로 받으므로 `SignatureException`·`MalformedJwtException`·`UnsupportedJwtException`이
한 번에 덮이고, jjwt가 타입을 추가해도 썩지 않는다. (3번 문제 해소)

이러면 두 출구가 **하드코딩 문자열 없이** 동일한 로직이 된다. (4번 문제 해소)

```text
// JwtAuthenticationEntryPoint — instanceof 나열이 통째로 사라진다
if (exception instanceof BusinessException be) {
    errorName = be.getClass().getSimpleName();
    message   = be.getMessage();
}

// GlobalExceptionHandler:26-29 — 이미 동일하게 동작 중
.error(e.getClass().getSimpleName())
.message(e.getMessage())
```

### 예외 배치

| 상황 | 예외 | 위치 | 던지는 곳 | status |
|---|---|---|---|---|
| 만료 | `ExpiredTokenException` | `global/security/exception` (신규) | `JwtTokenProvider` | 401 |
| 위조/형식/빈 토큰 | `InvalidTokenException` | `global/security/exception` (신규) | `JwtTokenProvider` | 401 |
| 쿠키 없음 | `RefreshTokenNotFoundException` | `account/domain/exception` (기존 재활용) | `LoginService` | 401 |
| DB에 없음 | `TokenTheftDetectedException` | `account/domain/exception` (기존) | `LoginService` | 403 |

토큰 자체의 유효성은 `global`이 판정하고(필터·재발급 공통), 재발급 비즈니스 규칙은 `account`가
판정한다. 책임 경계가 계층과 일치한다.

### 기존 패턴에서 벗어나는 부분과 이유

**`global`에 도메인성 예외를 신규 정의한다.** 기존 예외는 모두 도메인 모듈에 있으나, 이 둘은
`global/security`의 필터와 `account`의 재발급이 **공유해야** 한다. `account`에 두면 EntryPoint가
참조할 때 `global` → `account` 역방향 의존이 생긴다. `global/exception/BusinessException`을
상속하므로 기존 예외 체계와는 일관된다.

### 채택하지 않은 대안

- **`GlobalExceptionHandler`에 jjwt 핸들러 추가** — 필터 경로는 도달조차 하지 않아 반쪽이고,
  `global`이 jjwt에 의존하게 된다.
- **`JwtAuthTokenAdapter`에서 변환** — 재발급 경로만 덮여 필터 경로와 어휘가 갈린다.
- **jjwt 예외를 그대로 응답에 노출** — `error` 값이 라이브러리 클래스명이 되어 jjwt 업그레이드가
  API 계약 파괴가 된다. `httpStatus`도 못 실어 매핑 테이블이 두 출구에 각각 생긴다.
- **`HandlerExceptionResolver`로 필터 예외를 MVC에 위임** — 필터가 응답을 직접 쓰면 체인이 끊겨
  **만료된 access token을 들고 오는 재발급 요청(정상 시나리오)이 거부된다.**

---

## 변경 파일 목록

### 신규

| 파일 | 할 일 |
|---|---|
| `global/security/exception/ExpiredTokenException.java` | `BusinessException` 상속, 401, "만료된 토큰입니다." |
| `global/security/exception/InvalidTokenException.java` | `BusinessException` 상속, 401, "유효하지 않은 토큰입니다." |

### 수정

| 파일 | 할 일 |
|---|---|
| `global/security/JwtTokenProvider.java` | `validateToken` catch 4개 → 변환 catch 2개. `log.error` 4줄 제거. `import io.jsonwebtoken.*` 유지(이 클래스만 jjwt 참조) |
| `global/security/JwtAuthenticationFilter.java` | `catch (Exception)` 분리 — `BusinessException`은 `debug`(예상된 인증 실패), 그 외는 `error`(예상 밖 결함). **두 경우 모두 체인은 계속 진행** |
| `global/security/JwtAuthenticationEntryPoint.java` | `instanceof` 나열 → `instanceof BusinessException` 단일 분기. jjwt import 2개 제거. `log.error` → `log.warn` |
| `account/application/service/LoginService.java` | `reissue` 진입부에 쿠키 null/blank 체크 → `RefreshTokenNotFoundException`. 도달 불가능한 `:93-95` 제거. `ExpiredRefreshTokenException` import 제거 |
| `account/domain/RefreshToken.java` | `isExpired():40` 삭제 (호출부 0, KST/UTC 9시간 불일치) |
| `account/domain/exception/RefreshTokenNotFoundException.java` | 메시지를 쿠키 부재 전용으로 교체 (현재 "없거나 만료되었습니다"는 이제 구분되는 두 상황을 뭉뚱그림) |

### 삭제

| 파일 | 사유 |
|---|---|
| `account/domain/exception/ExpiredTokenException.java` | `global` 신규 클래스로 대체. 이름 중복 혼동 방지 |
| `account/domain/exception/InvalidJwtException.java` | 동일 |
| `account/domain/exception/ExpiredRefreshTokenException.java` | `:93-95` 제거 후 참조 0 |

### 변경 없음 (확인 완료)

- `JwtAuthTokenAdapter` — 순수 위임 유지. try/catch 불필요.
- `GlobalExceptionHandler` — 기존 `BusinessException` 핸들러(`:22`)가 `httpStatus`로 401을 반환.
  새 핸들러 불필요.
- `TokenTheftDetectedException` 및 재사용 탐지 로직(`:87-88`) — 의도된 방어 동작.

---

## 예상 사이드 이펙트

| 영역 | 영향 | 판단 |
|---|---|---|
| **프론트 API 계약** | 필터 경로 `error`가 `TokenExpiredException` → `ExpiredTokenException`, `InvalidTokenException`은 값 유지. 재발급 경로는 500 → 401 | 프론트는 401만 보고 `error` 문자열을 읽지 않음(확인됨). 안전하나 프론트 쪽에 기록 필요 |
| **`account` 모듈** | **새 import 없음.** `LoginService`는 새 예외를 잡지도 던지지도 않고 통과시킴 | 없음 |
| **jjwt 참조 범위** | `src/main` 내 jjwt import가 2개 파일 → **1개(`JwtTokenProvider`)로 축소** | 개선. 향후 0.12 업그레이드 영향이 한 클래스로 국한 |
| **OAuth2 경로** | `OAuth2AuthenticationSuccessHandler`는 토큰 생성만 호출 | 영향 없음 |
| **`getExpirationTime()`** | 변환하지 않음. 갓 생성한 토큰에만 호출되어 실패 도달 불가 | 그대로 유지 |
| **로그량** | 만료 1건당 `error` 3줄 → `warn` 1줄 | 개선 |
| **기존 테스트** | `JwtTokenProviderTest:41-49`가 `ExpiredJwtException`을 단언 → **반드시 깨짐** | 아래 테스트 전략에서 갱신 |

---

## 테스트 전략

기본 프로토콜(`harness/TESTING-GUIDE.md`)을 따르되, 아래 두 가지가 다르다.

**① 필터 경로는 컨트롤러 슬라이스로 검증할 수 없다.**
`BaseControllerTest`는 `@AutoConfigureMockMvc(addFilters = false)`이고 `JwtAuthenticationFilter`가
`@MockitoBean`이다. 따라서 A 경로(필터 → EntryPoint)는 **EntryPoint 단위 테스트로 검증**한다.
전 컨텍스트 `@SpringBootTest`는 Testcontainers 3종을 띄워야 해 비용 대비 이득이 없다.

**② 두 출구의 `error` 일관성을 별도 계약 테스트로 못박는다.**
이번 태스크의 핵심 산출물이므로, 구조적으로 보장되더라도 회귀 방지 테스트를 둔다.

| 테스트 | 유형 | 내용 |
|---|---|---|
| `global/security/JwtTokenProviderTest` (기존 이동·수정) | 단위 | 만료 → `ExpiredTokenException`, 서명 위조 → `InvalidTokenException`, 형식 오류 → `InvalidTokenException`, null/빈 문자열 → `InvalidTokenException`. 기존 `ExpiredJwtException` 단언 교체 |
| `global/security/JwtAuthenticationEntryPointTest` (신규) | 단위 | attribute에 `ExpiredTokenException` → 401 + `error`/`message`가 예외에서 파생. attribute 없음 → 기본 `UnAuthorizedException` |
| `JwtAuthenticationFilterTest` (기존 보강) | 단위 | **검증 실패 시에도 `filterChain.doFilter()`가 호출된다** — permitAll 엔드포인트 보호 회귀 방지. attribute 저장 확인 |
| `AccountControllerTest` (기존 보강) | 슬라이스 | `reissue`가 각 예외에 대해 401/401/401/403 + `error` 필드 반환 |
| `LoginServiceTest` (기존 보강) | 단위 | `reissue`: 쿠키 null/blank → `RefreshTokenNotFoundException`(provider 미호출), DB 없음 → `TokenTheftDetectedException` + `deleteAllByUserId` 호출 |
| `global/security/AuthErrorResponseConsistencyTest` (신규) | 단위 | 동일한 `ExpiredTokenException`에 대해 EntryPoint가 쓴 `error`와 `GlobalExceptionHandler`가 반환한 `error`가 **일치**함을 단언 |

### 테스트 파일 위치에 대한 제안

`TESTING-GUIDE.md`는 "테스트 폴더 구조를 소스와 일치시킨다"고 규정하나, 현재
`JwtTokenProviderTest`·`JwtAuthenticationFilterTest`가 `account/adapter/in/web/jwt/`에 있다
(소스는 `global/security/`). 어차피 두 파일을 크게 수정하므로 **`global/security/`로 이동**을
함께 제안한다. 신규 테스트는 규칙대로 `global/security/`에 둔다.
→ 이동을 원치 않으면 기존 위치를 유지하고 신규 파일만 `global/security/`에 두겠다.

---

## 승인 요청

위 방향으로 진행해도 될지 확인 부탁해. 승인 시 `main`에서
`ai/fix-token-reissue-error-response` 브랜치를 생성하고 구현에 들어간다.
