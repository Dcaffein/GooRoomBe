# 인증 설계 (Authentication)

회원가입·로그인·토큰의 동작과 그 근거를 정리한다.
**"왜 이렇게 되어 있는가"가 주 목적이며, 여기 적힌 불변식을 깨는 변경은 하지 않는다.**

---

## 1. 관통하는 원칙

> **증명되기 전에는 신원 키가 아니다.**

`users.email`은 UNIQUE이며 전역 신원 키다. 따라서 **이메일 소유가 증명되기 전에는
`users` / `auths`에 어떤 행도 만들지 않는다.** 이 원칙에서 아래가 따라 나온다.

| 결과 | 의미 |
|---|---|
| `Auth` 행의 존재 = 이메일 소유 증명 완료 | `verified` 플래그가 존재하지 않는다 |
| "미인증 계정"이라는 상태가 없다 | 그 상태를 관리·정리할 코드가 필요 없다 |
| 로컬과 OAuth가 같은 모델 | 경로별 분기가 없다 |

**모든 가입 경로는 이메일 소유 증명을 선행 조건으로 하며, 계정은 증명 이후에만 생성된다.
OAuth는 공급자가 증명하고, 로컬은 인증 메일이 증명한다. 차이는 그것뿐이다.**

---

## 2. 로컬 회원가입

자격증명(비밀번호)을 **증명이 끝난 뒤에** 받는다. 링크를 클릭한 사람이 비밀번호를 정한다.

```
[1] POST /api/auth/verifications        { email, redirectPage }
      └ 미가입 → 토큰 발급 + 가입 링크 메일
        기가입 → 토큰 없이 "이미 가입된 계정" 안내 메일
      └ 두 경우 모두 201 (구분은 메일 내용으로만)

    ↓ 사용자가 메일의 링크 클릭 → 프론트 폼 페이지 (?token=...)

[2] GET /api/auth/verifications/{token}
      └ 200 { email }  — 폼을 그리기 전 유효성 확인
        410 InvalidVerificationTokenException — 만료·위조·사용됨

[3] POST /api/auth/users                { token, password, nickname }
      └ 토큰 소비(GETDEL) → User(ACTIVE) + Auth(LOCAL) 생성
      └ 201 + access_token / refresh_token 쿠키 (자동 로그인)
```

### 설계 근거

**왜 이메일을 먼저 받고 비밀번호를 나중에 받나**
자격증명을 먼저 저장하면, 공격자가 피해자 주소로 폼을 제출한 뒤 피해자가 요청한 적 없는
메일을 클릭했을 때 **공격자의 비밀번호로 계정이 생성된다.** 증명이 끝난 뒤에 받으면
클릭한 사람(=이메일 주인)이 비밀번호를 정하므로 제3자가 얻는 것이 없다.

**왜 `POST /users`가 이메일을 받지 않나**
토큰이 가리키는 이메일을 서버가 쓴다. 클라이언트가 이메일을 함께 보내면
토큰과 다른 주소로 계정을 만들 여지가 생긴다.

**왜 재발송 엔드포인트가 없나**
`POST /verifications`를 다시 부르는 것이 곧 재발송이다. 별도 개념이 아니다.

**왜 접수 응답이 항상 201인가**
등록 여부로 응답이 갈리면 계정 열거 오라클이 된다. 화면 문구는 어느 경우든
"메일을 보냈습니다"로 같으므로 UX 손실이 없고, 실제 상황은 이메일 주인만 알게 된다.

### pending signup 저장소

```
Redis  account:signup:{token} → email     TTL 1시간
```

| 특성 | 이유 |
|---|---|
| **자격증명을 담지 않는다** | 저장소가 노출되어도 비밀번호가 새지 않는다 |
| **소비는 GETDEL로 원자적** | 동시 요청이 같은 토큰으로 두 번 계정을 만들 수 없다 |
| **이메일 기준 dedup 없음** | 기존 토큰을 무효화하면 공격자가 반복 접수로 피해자의 링크를 계속 죽일 수 있다. 같은 이메일의 토큰 여러 개가 공존해도 안전하다 — 각 토큰은 클릭한 사람이 비밀번호를 정한다 |
| **TTL 1시간** | 이 링크는 사실상 비밀번호 설정 권한이다. 만료 비용은 "이메일 재입력 한 번"뿐이라 짧게 잡는다 |

---

## 3. OAuth 회원가입 / 로그인 (Google)

```
/oauth2/authorization/google
    → CustomOAuth2UserService.loadUser()
        ├ email_verified 검증 ← 실패 시 OAuth2AuthenticationException
        └ SignupUseCase.registerOAuthUser()
            ├ 기존 유저 없음 → User(ACTIVE) 생성 + UserActivatedEvent
            └ 기존 유저 있음 → 유저 재사용
            └ 같은 provider auth가 없으면 Auth(GOOGLE) 추가
    → OAuth2AuthenticationSuccessHandler
        → issueTokens() → 쿠키 발급 → 프론트로 리다이렉트
```

### `email_verified`를 검증하는 이유

공급자가 소유를 검증하지 않은 주소는 신원 키로 쓸 수 없다. 검증 없이 받아들이면
**남의 이메일을 적어 넣은 공급자 계정으로 기존 계정에 연동**되는 길이 열린다.

이메일이 일치한다는 이유만으로 계정을 병합하는 것 자체가 위험한 패턴이며,
**양쪽 이메일이 모두 증명된 경우에만** 자동 연동이 허용된다. 로컬 측은 이 설계에서
항상 증명 완료이므로, 남는 요구사항이 공급자 측 `email_verified` 검증이었다.

---

## 4. 로그인

```
POST /api/auth/tokens   { email, password }
    → 201 + access_token / refresh_token 쿠키
```

**실패는 세 경우 모두 동일한 401 + 동일한 본문이다.**

| 실패 상황 | 응답 |
|---|---|
| 미가입 이메일 | `401 InvalidCredentialsException` |
| LOCAL 자격증명 없음 (구글 전용 계정) | 〃 (동일) |
| 비밀번호 불일치 | 〃 (동일) |

```json
{ "error": "InvalidCredentialsException", "message": "이메일 또는 비밀번호가 올바르지 않습니다." }
```

### 규약

- **실패 사유는 서버 로그(`warn`)에만 남긴다.** 응답이 갈리면 비밀번호를 몰라도
  상태코드만으로 가입 여부를 판별할 수 있다.
- **예외 메시지는 생성자에서 고정한다.** 호출부가 메시지를 주입할 수 있으면
  경로별로 문구가 갈려 열거가 다시 열린다.
- **application 계층은 Spring Security 예외를 던지지 않는다.**
  `BadCredentialsException`은 `BusinessException` 규약 밖이라 catch-all로 떨어져 500이 되며,
  헥사고날 구조에서 계층 위반이기도 하다.

> ⚠️ `GlobalExceptionHandler`는 `e.getMessage()`를 그대로 응답 본문에 싣는다.
> **예외 메시지에 넣는 모든 값이 외부로 나간다.** 내부 PK 등을 메시지에 넣지 말 것.

---

## 5. 토큰

| 토큰 | 쿠키명 | 기본 TTL | 저장 |
|---|---|---|---|
| Access | `access_token` | `JWT_ACCESS_EXPIRATION` (운영 3600s) | 없음 (stateless) |
| Refresh | `refresh_token` | `JWT_REFRESH_EXPIRATION` (기본 604800s) | `refresh_tokens` 테이블 |

- HMAC-SHA512. 쿠키 속성: `HttpOnly`, `SameSite=Lax`, `Path=/`,
  `Secure`는 `COOKIE_SECURE` (기본 `false` — **운영에서는 반드시 `true`**).
- 인증은 `JwtAuthenticationFilter`가 `access_token` 쿠키에서 수행한다.
  **검증에 실패해도 체인을 끊지 않는다** — 여기서 응답을 쓰면 `permitAll` 엔드포인트가 막히고,
  특히 만료된 access token을 들고 오는 재발급 요청(정상 시나리오)이 컨트롤러에 도달하지 못한다.

### 재발급 `PATCH /api/auth/tokens`

```
쿠키 없음        → 401 RefreshTokenNotFoundException
만료·위조        → 401 ExpiredTokenException / InvalidTokenException
유효하나 DB에 없음 → 403 TokenTheftDetectedException + 해당 유저의 refresh token 전량 삭제
정상             → 200 + 회전된 새 토큰 쌍
```

- **jjwt 예외 → 도메인 예외 변환은 `JwtTokenProvider` 한 곳에서만 한다.**
- 인증 실패 응답의 출구는 둘이며 구조적으로 합쳐지지 않는다 —
  `JwtAuthenticationEntryPoint`(필터 체인)와 `GlobalExceptionHandler`(Spring MVC).
  `@RestControllerAdvice`는 `DispatcherServlet` 내부 예외만 보므로 필터 예외에 도달할 수 없다.
  → 두 출구가 **같은 예외 타입**을 보게 만들어 응답 일관성을 확보했다.
  `AuthErrorResponseConsistencyTest`가 이 계약을 고정한다.
- 두 경로가 공유하는 예외는 `account`가 아니라 **`global/security/exception`**에 둔다
  (`account`에 두면 `global` → `account` 역의존 발생).

### 로그아웃 `DELETE /api/auth/tokens`

`refresh_tokens` 행을 삭제하고 쿠키를 만료시킨다. FCM 토큰이 함께 오면 기기 등록도 해제한다.

> **access token은 만료까지 유효하다.** 쿠키 삭제 ≠ 토큰 무효화이며 블랙리스트가 없다.
> access TTL이 1시간이라 리스크 창을 그 크기로 받아들인 결정이다.

---

## 6. 공개 엔드포인트

`SecurityConfig`에서 `permitAll`인 것 (그 외 전부 인증 필요):

```
POST   /api/auth/users
POST   /api/auth/tokens
PATCH  /api/auth/tokens
DELETE /api/auth/tokens
POST   /api/auth/verifications
GET    /api/auth/verifications/*      ← 메서드·깊이를 명시해 좁게 연다
/oauth2/**, /login/oauth2/**
```

`GET /api/auth/verifications/*`를 `/**`로 넓히지 않는다. 그러면 이 경로 아래에
추가되는 엔드포인트가 **자동으로 공개**된다.

---

## 7. 지켜야 할 불변식

변경 시 아래를 깨지 않는지 확인한다.

1. **증명되지 않은 이메일로 `users`/`auths`에 행을 만들지 않는다.**
2. **기존 자격증명을 덮어쓰는 경로를 만들지 않는다.** 재가입 요청이 기존 행을 변경하면
   제3자가 심은 비밀번호를 이메일 주인이 대신 승인해주는 계정 선점 탈취가 성립한다.
   (`Auth.overwritePassword`가 정확히 이 문제였고, 가드 추가가 아니라 **삭제**로 해결했다.)
3. **이메일 문자열 일치만으로 계정을 병합하지 않는다.** 양쪽 모두 증명된 경우에만.
4. **인증 실패 응답을 경로별로 다르게 만들지 않는다.**
5. **`UserActivatedEvent`는 `save()` 직후, 트랜잭션 안에서 발행한다.**
   `@GeneratedValue`라 `save()` 전에는 `id`가 null이고,
   `UserOutboxEventListener`가 `BEFORE_COMMIT`이라 트랜잭션 밖에서 발행하면 **리스너가 아예
   실행되지 않는다.** → outbox 행 없음 → Neo4j `SocialUser` 노드 없음.
   **에러가 나지 않고 조용히 깨지므로** 가입 경로를 건드릴 때마다 확인할 것.

---

## 8. 알려진 한계

| 항목 | 현재 | 비고 |
|---|---|---|
| **비밀번호 재설정** | **없음** | 비밀번호를 잊으면 복구 수단이 없다. 인증 링크가 선점되면 회수도 불가. 다음 task |
| **레이트 리밋** | 없음 | 크리덴셜 스터핑·메일 발송 남용 무방비. SMTP 쿼터 소진 시 가입 플로우 전면 중단 → task-91 |
| 타이밍 side-channel | 대응 없음 | 미가입 이메일은 BCrypt를 타지 않아 응답 시간이 갈린다 |
| access token 무효화 | 불가 | 위 5장 참조 |
| `refresh_tokens` 정리 | 스케줄러 없음 | 만료 행이 누적된다 |

---

## 9. 관련 파일

| 관심사 | 위치 |
|---|---|
| 가입·로그인 유스케이스 | `account/application/service/{Signup,Verification,Login}Service` |
| pending signup 저장소 | `account/domain/repository/PendingSignupRepository` + `adapter/out/persistence/PendingSignupRedisAdapter` |
| 도메인 | `account/domain/{User,Auth}` |
| 웹 어댑터 | `account/adapter/in/web/AccountController` |
| OAuth | `account/adapter/in/web/OAuth2/` |
| 보안 설정·필터·쿠키 | `global/security/` |
| 배경과 판단 근거 | `harness/tasks/task-90-signup-pre-verification.md` |
