# Task-91: OAuth2 인증 실패 처리 부재

## 배경

`SecurityConfig:63-68`의 `oauth2Login` 설정에 **`failureHandler`가 없다.**

```java
.oauth2Login(oauth2 -> oauth2
        .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService)
        )
        .successHandler(oauth2SuccessHandler)   // 성공만 처리
);
```

성공은 `OAuth2AuthenticationSuccessHandler`가 쿠키를 심고 `frontendBaseUrl`로 돌려보내는데,
**실패는 아무 데도 가지 못한다.**

### 실패 시 실제로 일어나는 일

Spring Security 기본값인 `SimpleUrlAuthenticationFailureHandler`가 `/login?error`로 리다이렉트한다.
그런데 그 경로는 `SecurityConfig:43-61`의 `permitAll` 목록에 없다.

```
OAuth 실패 → /login?error 리다이렉트 → anyRequest().authenticated()
           → JwtAuthenticationEntryPoint → 401 JSON
```

사용자는 **백엔드 도메인에서 401 JSON 원문**을 보게 된다. 프론트로 돌아갈 방법이 없고,
브라우저 주소창은 API 서버에 머문다.

### 도달 경로

| 상황 | 언제 | 비고 |
|---|---|---|
| 사용자가 구글 동의 화면에서 **취소** | 상시 | **정상적인 사용자 행동인데 401 JSON을 본다.** 가장 흔하다 |
| `email_verified: false` | task-90에서 신설 | `CustomOAuth2UserService:32-36`이 `OAuth2AuthenticationException`을 던진다 |
| 지원하지 않는 provider | `OAuth2UserInfoMapper.parse` 실패 | 현재 구글뿐이라 발생 가능성 낮음 |
| 토큰 교환·공급자 통신 실패 | 장애 시 | |

**첫 번째 항목은 task-90 이전부터 존재하던 결함이다.** 두 번째는 이번에 새로 생긴 경로이고,
`isEmailVerified()`가 fail-closed(클레임이 `null`이면 `false`)라 **클레임이 오지 않으면
모든 구글 로그인이 이 경로로 떨어진다.**

## 작업 범위

### 포함

1. `OAuth2AuthenticationFailureHandler` 신설 — `frontendBaseUrl` 기반 경로로 리다이렉트
2. `SecurityConfig`에 배선
3. 실패 사유를 프론트가 구분할 수 있도록 **고정 코드**를 쿼리 파라미터로 전달

### 제외

- 프론트의 실패 화면 구현 — 별도 task (프론트 저장소)
- OAuth 공급자 추가

## 구현 시 주의

### 1. 예외 메시지를 리다이렉트 URL에 그대로 싣지 않는다

`OAuth2AuthenticationException`의 메시지나 `OAuth2Error`의 `description`을 쿼리에 붙이면
백엔드 내부 표현이 URL에 노출되고, 프론트가 그대로 렌더링할 경우 반사형 XSS 표면이 된다.
Task-05·task-90에서 세운 "백엔드 원문을 화면에 태우지 않는다" 원칙과 같다.

**화이트리스트 코드만 넘긴다.** 예:

| 코드 | 상황 | 프론트 문구 |
|---|---|---|
| `email_not_verified` | `email_verified: false` | "구글에서 이메일 인증이 완료되지 않은 계정입니다" |
| `cancelled` | 사용자가 동의 취소 | 조용히 로그인 화면 복귀 |
| `failed` | 그 외 전부 | "로그인에 실패했습니다. 잠시 후 다시 시도해주세요" |

`CustomOAuth2UserService:34`가 이미 `new OAuth2Error("email_not_verified")`로 코드를 심어두었으므로
`getError().getErrorCode()`를 화이트리스트에 대조해 매핑하면 된다.

### 2. 취소와 실패를 구분한다

사용자가 동의 화면에서 취소한 것은 오류가 아니다. 에러 문구를 띄우면 안 된다.
구글은 `error=access_denied`로 돌려보낸다.

### 3. 리다이렉트 대상은 `frontendBaseUrl`을 써야 한다

`OAuth2AuthenticationSuccessHandler:24`가 이미 `@Value("${app.frontend.base-url}")`를 주입받고 있다.
같은 값을 쓰지 않으면 로컬(`localhost:3000`)과 운영에서 동작이 갈린다.

### 4. 프론트 경로를 백엔드가 정하게 되는 구조에 주의

성공 핸들러는 `frontendBaseUrl` 루트로 보내고 있다. 실패 경로(`/login` 등)를 백엔드 상수로 박으면
프론트 라우팅 변경 시 함께 고쳐야 한다. 상수 하나로 모아두고 문서에 남긴다.

## 검증

- **동의 취소**: 구글 동의 화면에서 취소 → 백엔드 401 JSON이 아니라 프론트 로그인 화면으로 복귀
- **`email_verified: false`**: 실제 재현이 어려우므로 `CustomOAuth2UserService`를 스텁으로 교체한
  통합 테스트로 고정. 리다이렉트 `Location` 헤더에 `frontendBaseUrl`과 `email_not_verified`가
  담기는지 확인
- **예외 원문 미노출**: `Location` 헤더에 예외 메시지·클래스명이 없는지
- **성공 경로 회귀**: 정상 구글 로그인이 기존대로 쿠키 2개 + 루트 리다이렉트

## 관련

- `CustomOAuth2UserService:32-36` — `email_verified` 게이트 (task-90에서 추가)
- `OAuth2AuthenticationSuccessHandler` — 성공 경로, 대칭 참고
- `SecurityConfig:37-39` — `JwtAuthenticationEntryPoint`. 실패가 여기로 떨어지는 원인

## Result

_미착수_
