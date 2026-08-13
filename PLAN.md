# PLAN — task-91: OAuth2 인증 실패 처리 추가

## 작업 목표

OAuth 로그인이 실패했을 때 사용자를 **프론트엔드로 돌려보낸다.** 현재는 백엔드 도메인에서
401 JSON 원문을 보게 되고 돌아갈 방법이 없다.

---

## 현황 분석

### 실패가 갈 곳이 없다

`SecurityConfig:63-68`의 `oauth2Login`에 `successHandler`만 있고 `failureHandler`가 없다.

```java
.oauth2Login(oauth2 -> oauth2
        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
        .successHandler(oauth2SuccessHandler)   // 성공만 처리
);
```

Spring Security 기본값 `SimpleUrlAuthenticationFailureHandler`가 `/login?error`로 리다이렉트하는데,
그 경로는 `SecurityConfig:43-61`의 `permitAll` 목록에 없다.

```
OAuth 실패 → /login?error → anyRequest().authenticated()
           → JwtAuthenticationEntryPoint → 401 JSON (백엔드 도메인)
```

성공은 `OAuth2AuthenticationSuccessHandler:36`이 `frontendBaseUrl`로 돌려보내는데
실패만 대칭이 깨져 있다.

### 도달 경로

| 상황 | 예외/코드 | 빈도 |
|---|---|---|
| **동의 화면에서 취소** | `access_denied` (구글이 반환) | **상시. 정상적인 사용자 행동이다** |
| `email_verified: false` | `email_not_verified` | task-90 이후. `CustomOAuth2UserService:32-36` |
| 토큰 교환·공급자 통신 실패 | 다양 | 장애 시 |
| 지원하지 않는 provider | `OAuth2UserInfoMapper.parse` 실패 | 현재 구글뿐이라 낮음 |

**첫 번째가 task-90 이전부터 있던 결함이고 가장 흔하다.** 취소는 오류가 아닌데 오류 화면조차
아닌 JSON 원문을 보게 된다.

---

## 구현 방향

### **302 리다이렉트**

JSON을 반환하지 않는다. **브라우저를 프론트엔드 로그인 페이지로 돌려보낸다.**

이 시점의 요청은 API 호출이 아니다. 사용자는 구글에서 우리 백엔드로 리다이렉트되어 온 상태이고,
**주소창이 백엔드 도메인에 있으며 응답을 기다리는 JS가 없다.** 여기서 JSON을 내려주면 브라우저가
그 JSON을 화면에 그대로 그린다. 지금 401 JSON이 보이는 게 정확히 그 상황이다.

그래서 유일한 복귀 수단이 리다이렉트다. 실패 사유는 **쿼리 파라미터 하나**로 함께 넘긴다.

```
302 Location: http://localhost:3000/login?error=failed
```

### 1. `OAuth2AuthenticationFailureHandler` 신설

`OAuth2AuthenticationSuccessHandler`와 같은 패키지에 대칭으로 둔다.
`SimpleUrlAuthenticationFailureHandler`를 상속해 `onAuthenticationFailure`만 재정의한다.

```java
String code = resolveCode(exception);            // 화이트리스트
String target = (code == null)
        ? frontendBaseUrl + LOGIN_PATH                       // 취소 — 깨끗하게
        : frontendBaseUrl + LOGIN_PATH + "?error=" + code;   // 그 외
log.warn("[oauth2] 인증 실패. code={}", code, exception);     // 원문은 로그에만
getRedirectStrategy().sendRedirect(request, response, target);
```

### 2. `error` 값은 **미리 정해둔 문자열 2개만** 쓴다

예외에서 꺼낸 값을 그대로 URL에 넣지 않는다. Spring이 주는 에러 코드는 상황에 따라
`invalid_token_response`, `authorization_request_not_found` 등 무엇이든 올 수 있는데,
**그런 값을 그대로 흘려보내면 프론트가 알 수 없는 값을 받게 되고 백엔드 내부 표현이 URL에 남는다.**

그래서 아는 값만 골라 정해진 문자열로 바꾸고, 나머지는 전부 하나로 뭉갠다.

| 실제 상황 | `error` 값 | 프론트 문구 |
|---|---|---|
| 동의 화면에서 취소 (`access_denied`) | **붙이지 않음** — `/login` | 없음. 그냥 로그인 화면 |
| 이메일 미검증 (`email_not_verified`) | `email_not_verified` | "구글에서 이메일 인증이 완료되지 않은 계정입니다" |
| **그 외 전부** | `failed` | "로그인에 실패했습니다. 잠시 후 다시 시도해주세요" |

구현은 `OAuth2AuthenticationException`이면 `getError().getErrorCode()`를 읽어 위 표에 대조하고,
표에 없거나 `OAuth2AuthenticationException`이 아니면 `failed`로 떨어뜨린다.
**실제 사유는 `log.warn`에 예외째로 남기므로 디버깅 정보는 잃지 않는다.**

**취소에 파라미터를 붙이지 않는 이유:** 원하는 화면이 "그냥 로그인 폼"이라 프론트가 분기할
필요가 없다. 코드를 주면 프론트에 "이 코드는 무시" 분기가 하나 생길 뿐이다.

### 3. 예외 원문을 URL에 싣지 않는다

`exception.getMessage()`나 `OAuth2Error.getDescription()`을 쿼리에 붙이면 백엔드 내부 표현이
URL에 노출되고, 프론트가 그대로 렌더링하면 반사형 XSS 표면이 된다.
**Task-05·task-90의 "백엔드 원문을 화면에 태우지 않는다"와 같은 원칙이다.**

화이트리스트라 이론상 안전하지만 `URLEncoder`로 인코딩해 심층 방어를 둔다.

### 4. 리다이렉트 대상은 `frontendBaseUrl`

`OAuth2AuthenticationSuccessHandler:24`가 이미 `@Value("${app.frontend.base-url}")`를 쓴다.
같은 값을 쓰지 않으면 로컬(`localhost:3000`)과 운영이 갈린다.

경로(`/login`)는 **상수 하나로 모아둔다.** 백엔드가 프론트 라우팅을 알고 있는 구조라
흩어지면 프론트 경로 변경 시 찾기 어려워진다.

---

## 확정 사항

| # | 항목 | 결정 |
|---|---|---|
| 1 | 실패 처리 방식 | **302 리다이렉트.** JSON 응답이 아니다 |
| 2 | 취소(`access_denied`) 처리 | **파라미터 없이 `/login`으로.** 오류가 아니다 |
| 3 | `error` 값 | **정해둔 2개**(`email_not_verified`, `failed`)만. 예외 값을 그대로 흘리지 않는다 |
| 4 | 예외 원문 | **URL에 싣지 않는다.** 로그로만 |
| 5 | 프론트 화면 구현 | **이번 범위 밖.** 프론트 저장소 task로 전달 |

---

## 변경 파일 목록

### 신규

| 파일 | 내용 |
|---|---|
| `account/adapter/in/web/OAuth2/OAuth2AuthenticationFailureHandler.java` | 화이트리스트 매핑 + 리다이렉트 |
| `account/adapter/in/web/OAuth2/OAuth2AuthenticationFailureHandlerTest.java` | 단위 테스트 |

### 수정

| 파일 | 내용 |
|---|---|
| `SecurityConfig:63-68` | `.failureHandler(oauth2FailureHandler)` 배선 |

---

## 프론트엔드에 전달할 계약

프론트 저장소에서 별도 task로 처리한다. **task-40과 같은 패스에 넣어야 프론트 배포가 한 번으로 끝난다.**

```
성공  → {FRONTEND_BASE_URL}/                      (기존과 동일, 쿠키 2개 발급됨)
취소  → {FRONTEND_BASE_URL}/login                 (쿼리 없음. 에러 문구 띄우지 말 것)
실패  → {FRONTEND_BASE_URL}/login?error=email_not_verified
      → {FRONTEND_BASE_URL}/login?error=failed
```

`error` 값은 이 둘 외에 오지 않는다. 모르는 값이 오면 `failed`와 동일하게 처리하면 된다.

> **경로 `/login`은 프론트 라우팅에 맞춰 조정 가능하다.** 프론트 확인 후 확정한다.
> 다른 값이어도 백엔드는 상수 하나만 바꾸면 된다.

---

## 예상 사이드 이펙트

1. **기존 401 JSON 응답이 사라진다.** 프론트가 그 응답에 의존하는 코드는 없다(막다른 길이었음).
   API 클라이언트가 아니라 브라우저 리다이렉트 경로라 CORS 영향도 없다.
2. **`SimpleUrlAuthenticationFailureHandler`의 세션 처리.** 부모 구현은 실패 예외를 세션에 저장하는
   경로가 있으나 이 앱은 `SessionCreationPolicy.STATELESS`다. `onAuthenticationFailure`를 완전히
   재정의하므로 부모 로직을 타지 않는다 — **`super.onAuthenticationFailure`를 호출하지 말 것.**
3. **`/login?error`가 `permitAll`이 아닌 문제는 그대로 남지만 무해해진다.** 리다이렉트 대상이
   백엔드가 아니라 프론트 도메인이 되므로 `SecurityConfig`를 타지 않는다.
   **`permitAll` 목록에 `/login`을 추가하지 말 것** — 불필요하게 백엔드 경로를 여는 것이다.

---

## 테스트 전략

`TESTING-GUIDE.md` 기본 프로토콜을 따른다. 기존 `OAuth2AuthenticationSuccessHandlerTest`가
같은 형태의 단위 테스트 선례다.

**단위 (`OAuth2AuthenticationFailureHandlerTest`)**
- `access_denied` → `Location`이 `{frontendBaseUrl}/login`이고 **쿼리가 없는지**
- `email_not_verified` → `Location`에 `?error=email_not_verified`
- 임의의 다른 `OAuth2AuthenticationException` → `?error=failed`
- `OAuth2AuthenticationException`이 아닌 `AuthenticationException` → `?error=failed`
- **`Location`에 예외 메시지·클래스명·`description`이 없는지** (문자열 부재 검증)
- `frontendBaseUrl`이 적용되는지 (하드코딩된 호스트가 없는지)

**회귀**
- 성공 경로가 그대로인지 — `OAuth2AuthenticationSuccessHandlerTest` 통과 유지
- 전체 테스트 스위트 통과 (현재 550개)

**수동 검증 (배포 후)**
- 구글 동의 화면에서 **취소** → 프론트 로그인 화면으로 복귀, 에러 문구 없음
- `email_verified: false`는 실제 재현이 어려우므로 단위 테스트로 갈음한다

---

## 승인 대기

위 확정 사항 5개와 변경 파일 목록에 이견이 없으면 승인해 주기 바란다.
승인 시 `main`에서 `ai/fix-oauth2-failure-handler` 브랜치를 생성하고 구현에 들어간다.

프론트 경로(`/login`)는 프론트 확인 전이라도 상수로 두고 진행 가능하다 — 확정되면 한 줄 변경이다.
