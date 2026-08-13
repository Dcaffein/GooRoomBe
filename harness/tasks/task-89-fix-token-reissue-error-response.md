# Task-89: 토큰 재발급 실패 응답 정규화

## 배경

`PATCH /api/auth/tokens`(토큰 재발급)가 **정상적인 만료 상황에 500을 반환한다.**

`LoginService.reissue()`는 JWT 파싱을 가장 먼저 수행한다.

```java
AuthPrincipal authPrincipal = authTokenProvider.validateToken(oldRefreshTokenValue);  // :82
...
if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
    throw new ExpiredRefreshTokenException("리프레시 토큰이 만료되었습니다...");        // :94
}
```

`JwtTokenProvider.validateToken`이 던지는 예외는 모두 jjwt 예외(`ExpiredJwtException`,
`SignatureException`, `IllegalArgumentException`)이며, `BusinessException`을 상속하지 않는다.
`GlobalExceptionHandler`에 전용 핸들러가 없어 catch-all `@ExceptionHandler(Exception.class)`(`:107`)로
떨어져 **500 + `log.error` 스택트레이스**가 된다.

| 상황 | 현재 응답 | 빈도 |
|---|---|---|
| `refresh_token` 쿠키 없음 (`required = false` → null) | **500** | 비로그인 상태에서 프론트가 호출할 때마다 |
| **refresh token 만료** | **500** | **모든 사용자, 7일마다** |
| 서명 위조/변조 | **500** | 공격 시 |
| 유효하지만 DB에 없음 (`:88`) | 403 `TokenTheftDetectedException` | 정상 동작 |

### `:93-95`는 도달 불가능한 코드다

DB의 `expiryDate`는 JWT의 `exp`에서 그대로 뽑아온 값이다(`:67` `getExpirationTime`).
DB 기준으로 만료면 JWT 기준으로도 반드시 만료이므로, `:82`에서 `ExpiredJwtException`이 먼저 터진다.
따라서 `ExpiredRefreshTokenException`은 **한 번도 발생하지 않는다.**

**재발급 API가 존재하는 이유인 "만료 처리"가 정확히 500으로 나가고 있다.**
프론트는 5xx(장애, 재시도 안내)와 401(재로그인 유도)을 구분할 수 없어 재로그인을 유도하지 못한다.

### 이미 준비되어 있으나 배선되지 않은 예외 클래스

`account/domain/exception/`에 아래 클래스들이 존재하지만 `src/main` 어디에서도 `new` 되지 않는다.

| 클래스 | 정의된 상태코드 |
|---|---|
| `ExpiredTokenException` | 401 |
| `InvalidJwtException` | 401 |
| `RefreshTokenNotFoundException` | 401 |

셋 다 401로 정의되어 있어, **지금 500이 나가는 자리에 쓰라고 만들어둔 것으로 보인다.**
설계 의도가 구현에 배선되지 않은 상태다.

---

## Objective

`PATCH /api/auth/tokens`의 모든 실패 경로가 의미에 맞는 4xx로 응답하도록 한다.

- 만료 / 쿠키 없음 / 위조 상황에서 500이 발생하지 않을 것
- 프론트엔드가 "재로그인 필요"를 판별할 수 있을 것
- 도달 불가능한 만료 검증 코드(`:93-95`)를 정리할 것
- 정상적인 토큰 만료가 `log.error`로 기록되지 않을 것

---

## Decision

### 인증 실패 응답의 출구는 둘이며, 구조적으로 합쳐지지 않는다

| | `JwtAuthenticationEntryPoint` | `GlobalExceptionHandler` |
|---|---|---|
| 소속 | Security 필터 체인 | Spring MVC (`@RestControllerAdvice`) |
| 담당 | access token (인증 자체의 실패) | refresh token (재발급 요청의 실패) |
| 보는 예외 | 필터가 `request` attribute에 넣어준 것 | 컨트롤러 밖으로 나온 것 |

`@RestControllerAdvice`는 `DispatcherServlet` **내부**에서 발생한 예외만 본다. 필터는 그보다
앞단이므로 필터에서 터진 예외는 구조적으로 도달할 수 없다. 반대로 EntryPoint는 필터 체인의
장치이므로 MVC 예외를 볼 수 없다.

`HandlerExceptionResolver`를 필터에 주입해 MVC로 넘기는 기법은 **채택하지 않는다.** 현재 필터는
검증 실패 시 예외를 삼키고 `filterChain.doFilter()`를 계속 진행하는데, 이것이 `permitAll`
엔드포인트에 필수다. 필터가 응답을 직접 쓰면 체인이 끊겨, **만료된 access token을 들고 오는
재발급 요청**(= 정상 시나리오)이 컨트롤러에 도달하지 못하고 거부된다. 또한 토큰이 아예 없어
예외조차 발생하지 않는 경우는 넘길 예외가 없으므로 EntryPoint는 어차피 필요하다.

### 따라서 변환 지점을 `JwtTokenProvider` 하나로 모은다

두 출구가 **같은 예외 타입**을 보게 만들어 통일성을 구조적으로 보장한다.
그 공통 타입은 jjwt가 아니라 우리 예외여야 한다.

```
global/security/exception/
├── ExpiredTokenException     (BusinessException 상속, 401)
└── InvalidTokenException     (BusinessException 상속, 401)
```

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

두 출구가 하드코딩 문자열 없이 동일한 로직이 된다.

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

양쪽 모두 `getSimpleName()` + `getMessage()`이고 원천이 같은 클래스이므로,
`TokenExpiredException` vs `ExpiredTokenException` 같은 어휘 드리프트가 발생할 수 없다.

**jjwt 예외를 그대로 응답에 노출하지 않는 이유:**
- `error` 값이 라이브러리 클래스명(`ExpiredJwtException`, `SignatureException`)이 되어,
  jjwt 업그레이드가 곧 프론트 API 계약 파괴가 된다 (0.11의 `io.jsonwebtoken.SignatureException`은
  이미 deprecated).
- 빈 토큰은 jjwt 타입도 아니다 (`IllegalArgumentException`).
- jjwt 예외는 `httpStatus`를 들고 있지 않아, "예외 → status" 매핑을 두 출구에 각각 만들어야 한다.
  → 없애려던 이중 매핑으로 회귀한다.
- `AuthTokenProvider` 포트가 jjwt를 감추는 의미가 사라진다.

### 실패 경로별 응답 매핑

HTTP 상태는 **401로 통일**하고, `ErrorResponse.error`(예외 클래스명)로 원인을 구분한다.
프론트는 현재 401 단일 분기로 재로그인을 유도하고 있으며 그대로 두어도 동작한다.
`error` 필드는 추후 "세션 만료" / "로그인 필요" 문구 분리를 위한 선택지로만 열어둔다.

| 상황 | 예외 | 정의 위치 | 던지는 곳 | status |
|---|---|---|---|---|
| 만료 | `ExpiredTokenException` | `global/security/exception` (신규) | `JwtTokenProvider` | 401 |
| 서명 위조 / 형식 오류 / 빈 토큰 | `InvalidTokenException` | `global/security/exception` (신규) | `JwtTokenProvider` | 401 |
| `refresh_token` 쿠키 없음/공백 | `RefreshTokenNotFoundException` | `account/domain/exception` (기존) | `LoginService` | 401 |
| 유효하지만 DB에 없음 | `TokenTheftDetectedException` | `account/domain/exception` (기존) | `LoginService` | 403 |

토큰 자체의 유효성은 `global`이 판정하고(필터·재발급 공통), 재발급 비즈니스 규칙은 `account`가
판정한다. 책임 경계가 계층과 일치한다.

refresh token은 요청자 본인의 쿠키이므로 만료/위조 구분 노출에 계정 열거 위험이 없다.
오히려 위조 시도와 정상 만료를 분리 집계할 수 있어 운영상 이점이 있다.

쿠키 부재는 provider까지 가기 전에 `LoginService.reissue` 진입부에서 명시적으로 거른다.
(그대로 두면 `IllegalArgumentException` → `InvalidTokenException`으로 뭉뚱그려져 구분이 무너진다.)

**부수 효과**
- `JwtAuthTokenAdapter`는 순수 위임으로 남는다 (try/catch 불필요).
- `account`에 새 import가 생기지 않는다. `LoginService`는 이 예외들을 잡지도 던지지도 않고
  통과시키므로, jjwt든 global 예외든 모르는 상태 그대로다.
- `GlobalExceptionHandler`에 새 핸들러가 불필요하다. 기존 `BusinessException` 핸들러가
  `httpStatus`로 401을 반환한다.

### 유지

- **`TokenTheftDetectedException`(403)의 동작은 변경하지 않는다.** 토큰 재사용 탐지 시
  해당 사용자의 모든 refresh token을 삭제하는 현재 로직(`:87-88`)은 의도된 방어 동작이다.
- **정상 흐름의 로그 레벨은 `warn` 이하로 한다.** 토큰 만료는 장애가 아니다.

### 삭제 대상

- **`account/domain/exception/ExpiredTokenException`, `InvalidJwtException`** — `global`의 신규
  클래스로 대체된다. 이름이 겹쳐 혼동을 유발하므로 삭제한다.
  (당초 "기존 미사용 클래스 재활용" 방침이었으나, 변환 지점을 `JwtTokenProvider`로 옮기면서
  `account`가 아닌 `global`에 있어야 두 출구가 공유할 수 있다. `RefreshTokenNotFoundException`은
  `LoginService`가 던지므로 `account`에 그대로 둔다.)
- **`ExpiredRefreshTokenException`** — `:93-95` 제거 후 `src` 전체 참조가 0이 된다
  (현재 참조는 `LoginService:4` import와 `:94` 단 두 곳). 클래스 파일째 삭제한다.
  DB 기준 만료 검증이 필요해지면 그때 401로 다시 만든다.
- **`RefreshToken.isExpired()`** — 호출부 0이며 KST/UTC 9시간 어긋난 비교를 한다.
  살려두면 향후 오작동 지뢰이므로 삭제한다.

---

## 함께 확인할 것

- **`JwtTokenProvider:67`, `JwtAuthenticationEntryPoint:41`의 `SecurityException` 분기가 무효다.**
  → 아래 `SecurityException 오분기` 절 참조.
- **두 인증 경로의 `error` 값 어휘가 서로 다르다.** → 아래 `error 어휘 통일` 절 참조.
- **토큰 만료 1건당 `log.error`가 3곳에서 발생한다.** 정상 흐름이 error 레벨 로그를 쌓아
  실제 장애 알림을 오염시킨다. → 아래 `로그 레벨 조정` 절 참조.

### SecurityException 오분기

jjwt 0.11.5 실제 계층 (jar 확인):

```
java.lang.RuntimeException
└── io.jsonwebtoken.JwtException
    ├── io.jsonwebtoken.security.SecurityException
    │   └── io.jsonwebtoken.SignatureException            (root 패키지, deprecated)
    │       └── io.jsonwebtoken.security.SignatureException   ← 서명 실패 시 실제로 던져짐
    ├── io.jsonwebtoken.MalformedJwtException
    ├── io.jsonwebtoken.UnsupportedJwtException
    └── io.jsonwebtoken.ClaimJwtException
        └── io.jsonwebtoken.ExpiredJwtException
```

root 패키지에 `SignatureException`은 존재하지만 **`SecurityException`은 존재하지 않는다.**
따라서 `import io.jsonwebtoken.*` 하에서도 `catch (SecurityException ...)` /
`instanceof SecurityException`은 `java.lang.SecurityException`으로 해석되어 절대 걸리지 않는다.
컴파일은 통과하므로 드러나지 않는다.

**`Decision`의 변환 도입으로 두 지점 모두 자연히 해소된다.**

- `JwtTokenProvider` — `log + rethrow`뿐이던 4개 arm이 `ExpiredJwtException` /
  `JwtException | IllegalArgumentException` 2개 arm의 **변환** catch로 대체된다.
  `JwtException`으로 받으므로 `SignatureException`·`MalformedJwtException`·
  `UnsupportedJwtException`이 한 번에 덮이고, jjwt가 타입을 추가해도 썩지 않는다.
- `JwtAuthenticationEntryPoint` — `instanceof ExpiredJwtException` / `SecurityException`
  나열이 `instanceof BusinessException` 단일 분기로 대체되어 오분기 자체가 사라진다.

### error 어휘 통일

`Decision`의 "프론트가 `error` 필드로 원인을 구분한다"가 성립하려면 두 출구가 같은 어휘를
써야 하나, 현재 다르다.

| 상황 | 재발급 경로 | 필터 경로 (EntryPoint) |
|---|---|---|
| 만료 | (500으로 나가 `error` 없음) | `TokenExpiredException` |
| 위조/형식 | (500으로 나가 `error` 없음) | `InvalidTokenException` |

EntryPoint의 값은 하드코딩 문자열이라, 도메인 예외를 `account`에 두고 EntryPoint 리터럴을
거기에 맞추는 방식으로는 **컴파일러가 강제해주지 않아 드리프트가 재발한다.**
(`ExpiredTokenException` vs `TokenExpiredException`처럼 어순만 뒤집힌 사고가 실제로 나 있다.)

`Decision`대로 예외를 `global/security/exception`에 두면 두 출구가 같은 클래스를 참조하고
양쪽 다 `getSimpleName()` + `getMessage()`를 쓰므로, **하드코딩 문자열이 0이 되어 드리프트가
구조적으로 불가능해진다.** 별도 규약이나 주석이 필요 없다.

### 회귀 방지 테스트

구조적으로 보장되더라도, 두 경로가 같은 `error`를 낸다는 계약은 테스트로 못 박아둔다.

```text
만료된 access token으로 보호된 엔드포인트 호출  → 401, error = "ExpiredTokenException"
만료된 refresh token으로 PATCH /api/auth/tokens → 401, error = "ExpiredTokenException"
```

### 로그 레벨 조정

| 위치 | 현재 로그 | 조정 |
|---|---|---|
| `JwtTokenProvider:71` | `만료된 JWT 토큰입니다.` | 제거 (변환 catch로 대체) |
| `JwtAuthenticationFilter:39` | `JWT 인증 실패: {}` | `debug` (중복 제거) |
| `JwtAuthenticationEntryPoint:31` | `인증 실패 - 예외 타입: ...` | `warn` (단일 지점 유지) |

위조(`InvalidTokenException`)만 `warn`으로 남겨 정상 만료와 공격 시도를 로그에서 구분한다.

**`JwtAuthenticationFilter`의 `catch (Exception e)`를 분리한다.** 로그를 `debug`로 낮추면
인증과 무관한 진짜 버그(NPE 등)까지 조용히 삼켜진다. `BusinessException`은 `debug`(예상된 인증
실패), 그 외 `Exception`은 `error`(예상 밖 결함)로 나눈다. 체인은 두 경우 모두 계속 진행한다.

### 갱신이 필요한 기존 테스트

- `JwtTokenProviderTest:41-49` — `ExpiredJwtException` 발생을 단언한다.
  `ExpiredTokenException`으로 변경 필요. (`import io.jsonwebtoken.ExpiredJwtException` 제거)
- `JwtAuthenticationFilterTest`, `LoginServiceTest`, `AccountControllerTest` — 영향 확인 필요.

---

## Out of Scope

- `LoginService.login()`의 응답 정규화 및 계정 열거 대응 → **task-90**
- 인증 엔드포인트 레이트 리밋 → **폐기 (2026-08-13)**
- access token 무효화 수단(로그아웃 후에도 만료까지 유효) — 별도 논의
- `refresh_tokens` 테이블 만료 행 정리 스케줄러 부재 — 별도 논의
- **`BusinessException`에 명시적 에러 코드 필드 도입** — 현재 `error` 값이
  `getClass().getSimpleName()`이라 **클래스명 리팩터링이 곧 API 계약 파괴**다. 프로젝트 전체
  예외 클래스를 건드리는 작업이므로 별도 논의.
- **jjwt 0.12.x 업그레이드** — 0.11.5의 `io.jsonwebtoken.SignatureException`은 deprecated이고
  0.12에서 예외 구성이 재편된다. 본 태스크의 변환 도입으로 업그레이드 영향이
  `JwtTokenProvider` 한 클래스로 국한되므로, 업그레이드는 이후에 독립적으로 진행 가능하다.

---

## 브랜치

`ai/fix-token-reissue-error-response`
