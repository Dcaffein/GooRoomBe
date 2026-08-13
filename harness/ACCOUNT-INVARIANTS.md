# Account 도메인 불변식 및 흐름

`account` 도메인이나 `global/security`를 수정하기 전에 읽는다.
**설명서가 아니라 제약 목록이다.** 각 항목은 "깨뜨리면 무엇이 조용히 망가지는가"를 기준으로 적혀 있다.

흐름이 `AccountController` → 3개 서비스 → Redis 어댑터 → 이벤트 리스너 → 다른 도메인에 걸쳐 있어
**전체를 보여주는 파일이 코드에는 없다.** 이 문서가 그 자리다.

---

## 0. 최상위 원칙

> **증명되기 전에는 신원 키가 아니다.**

`users.email`이 UNIQUE이고 전역 신원 키다. 이 앵커가 증명 없이 만들어지면
계정 선점 탈취와 암묵적 계정 병합이 **둘 다 여기서 파생된다.**

따름정리: **계정 행은 이메일 소유가 증명된 뒤에만 만들어진다.** 그래서 `verified` 플래그가
존재하지 않고, 로컬과 OAuth가 단일 모델이다.

---

## 1. 불변식

### I-1. 계정 행이 생기는 지점은 `SignupService` 하나뿐이다

| | |
|---|---|
| 강제 위치 | `SignupService.signup` / `registerOAuthUser` |
| 강제 수단 | **관례뿐.** `User.createActive`가 유일 팩토리라는 것 외에 코드적 차단 없음 |
| 깨지면 | 증명 없는 계정이 생긴다. 설계 전체가 무너진다 |

예외: `DevUserService`(`@Profile("local")`). 운영에서는 빈이 만들어지지 않는다.

### I-2. 모든 계정은 인증 완료 상태다

| | |
|---|---|
| 강제 위치 | `User.createActive` — `UserStatus.ACTIVE` 고정. `UserStatus`에 `PENDING`이 없다 |
| 깨지면 | 미인증 계정 상태가 부활하고, 그것을 떠받치려면 정리 스케줄러·만료 정책이 함께 돌아온다 |

**`UserStatus`에 `PENDING`을 다시 추가하지 말 것.** 되돌리려면 task-90 전체를 되돌려야 한다.

### I-3. `Auth` 행의 존재가 곧 이메일 소유 증명이다

| | |
|---|---|
| 강제 위치 | `Auth.createLocalAuth` / `Auth.createOAuth`가 유일 생성 경로 |
| 강제 수단 | **관례뿐.** `@Builder`가 `private`이지만 클래스 내부에서는 열려 있다 |
| 깨지면 | 증명 없는 자격증명이 생긴다 |

**기존 행의 비밀번호를 덮어쓰는 메서드를 만들지 말 것.** `Auth.overwritePassword()`가
정확히 그 이유로 삭제됐다 — 재가입이 남의 비밀번호를 갈아치우는 계정 탈취였다.
비밀번호 변경이 필요하면 별도 플로우(task-94)로 간다.

### I-4. 이메일은 요청 본문이 아니라 토큰이 가리키는 값을 쓴다

| | |
|---|---|
| 강제 위치 | `SignupRequestDto`에 `email` 필드가 **없다** |
| 깨지면 | "A로 증명하고 B로 계정 생성"이 가능해진다. 증명이 무의미해진다 |

**`SignupRequestDto`에 `email`을 추가하지 말 것.**

### I-5. 토큰은 1회용이며, 조회와 삭제가 원자적이다

| | |
|---|---|
| 강제 위치 | `PendingSignupRedisAdapter.consumeEmailByToken` — Redis `GETDEL` |
| 깨지면 | 두 탭 동시 제출로 계정이 두 번 생성될 수 있다 |

**`findEmailByToken`(조회 전용)과 `consumeEmailByToken`(소비)의 구분을 없애지 말 것.**
`GET /verifications/{token}`이 소비하면 폼을 그리는 순간 토큰이 죽어 **가입이 절대 성립하지 않는다.**

부수 성질: 같은 이메일에 **유효한 토큰이 여러 개 존재할 수 있다.** 이메일 기준 dedup을 하지
않기 때문이며 의도된 것이다(재접수로 피해자 링크를 죽이는 DoS 방지). 역방향 인덱스를 추가하지 말 것.
남은 토큰은 I-6에 의해 무해하게 무력화된다.

### I-6. 계정 생성 직전에 이메일 중복을 다시 검사한다

| | |
|---|---|
| 강제 위치 | `SignupService:39-41` → `AlreadyRegisteredEmailException` (409) |
| 깨지면 | 링크를 여는 사이 다른 경로로 가입된 이메일에 중복 계정 시도가 발생한다 |

**검사 시점에 토큰은 이미 소비된 뒤다.** 409를 받은 링크는 죽어 있다.
프론트가 409에서 "다시 시도"를 유도하면 안 된다 — 대응은 "구글로 로그인"이다.

### I-7. 응답으로 계정 존재를 노출하지 않는다

| | |
|---|---|
| 강제 위치 | `VerificationService.requestVerification` — 양쪽 분기 모두 201 |
| 깨지면 | 미인증 상태에서 임의 이메일의 가입 여부를 판별할 수 있다 |

구분은 **발송되는 메일 내용으로만** 한다. 이미 계정이 있으면 토큰을 생성하지 않고
안내 메일만 보낸다. 정보가 사라지는 게 아니라 도달 채널이 바뀐다 — 메일함 주인만 안다.

**task-94에서 특히 중요해진다.** `/verifications`가 두 purpose를 받게 되고 `purpose`는
호출자가 정하므로, 한쪽 분기라도 존재 여부를 노출하면 **약한 분기가 엔드포인트 전체의
강도를 결정한다.**

같은 원칙: 로그인 실패 3경우(미가입 / LOCAL 자격증명 없음 / 비밀번호 불일치)는
**단일 401 + 동일 본문**이다(`LoginService.login`). 사유는 `log.warn`으로만 남긴다.

> **알려진 구멍:** `UserController:27-35`가 로그인한 사용자에게 임의 이메일의 존재를
> 404로 노출한다. 현재 열거 방어는 비용을 0에서 "계정 하나"로 올린 것이지 막은 게 아니다.

### I-8. `UserActivatedEvent`는 계정 생성과 같은 트랜잭션에서 발행한다

| | |
|---|---|
| 강제 위치 | `SignupService`의 클래스 레벨 `@Transactional` + `publishActivated`가 `signup()` 본문 안 |
| 깨지면 | **에러 없이 조용히 깨진다.** 가입도 로그인도 정상인데 Neo4j `SocialUser` 노드만 없다 |

`UserOutboxEventListener:36`이 `@TransactionalEventListener(BEFORE_COMMIT)`이라
**활성 트랜잭션이 없으면 리스너를 예외 없이 건너뛴다.**

깨뜨리는 방법 세 가지: 클래스 레벨 `@Transactional` 제거 / 호출을 트랜잭션 없는 호출자로 이동 /
`signup()`을 self-invocation으로 호출.

정상 경로에서는 outbox 행이 `users`·`auths` INSERT와 같은 트랜잭션으로 커밋되고,
이후 `UserOutboxRetryService`가 재처리하므로 **동기화 실패는 재시도로 회복된다.**
남는 성질은 최종 일관성뿐이다.

### I-9. 공급자가 검증하지 않은 이메일은 들이지 않는다

| | |
|---|---|
| 강제 위치 | `CustomOAuth2UserService:32-36` — `email_verified` 게이트 |
| 깨지면 | 남의 이메일을 적어 넣은 공급자 계정으로 기존 계정에 연동된다 |

**게이트가 어댑터 계층에 있고 `SignupService.registerOAuthUser`는 그것을 신뢰한다.**
`registerOAuthUser`의 호출자를 추가하면 게이트를 우회하게 된다. 현재 호출자는 한 곳뿐이다.

`isEmailVerified()`는 **fail-closed**다 — 클레임이 `null`이면 `false`. 즉 구글이 클레임을
보내지 않으면 모든 구글 로그인이 막힌다.

### I-10. 이메일이 일치하는 기존 계정에는 OAuth를 연동한다

| | |
|---|---|
| 강제 위치 | `SignupService:54-63` — `findByEmail` 후 `Auth` 행 추가 |

**이것은 결함이 아니라 설계대로다.** 계정의 신원 앵커가 "증명된 메일함 통제"이고
구글 로그인은 같은 사실을 다시 증명하므로, 자동 연동은 로컬/OAuth 단일 모델의 귀결이다.

구 모델(이메일이 증명되지 않던 시절)의 "암묵 병합 위험" 서술을 여기 적용하지 말 것.
**자동 연동을 거부·확인 방식으로 바꾸자는 제안을 먼저 꺼내지 말 것.**

받아들인 성질: **메일함 통제 = 계정 접근.** task-94(비밀번호 재설정)가 들어오면
서비스 전체의 전제가 되므로 일관적이다.

비대칭 주의: 로컬 재가입은 I-6에 의해 409로 **막힌다.** 그건 "연동"이 아니라
**비밀번호 교체** 요청이고, 그게 곧 비밀번호 재설정이라 범위 분리로 막아둔 것이다.

---

## 2. 흐름

### 로컬 가입

```
① POST /api/auth/verifications { email, redirectPage }        [permitAll]
   VerificationService.requestVerification
     ├ 계정 있음 → sendAlreadyRegisteredEmail → return  (토큰 생성 안 함)
     └ 계정 없음 → UUID v7 토큰 → Redis SET account:signup:{token} = email (TTL 1h)
                 → sendSignupVerificationEmail
   ⇒ 어느 쪽이든 201 (I-7)

② 메일 링크 = FRONTEND_BASE_URL + redirectPage + "?token="   (EmailAdapter:62)

③ GET /api/auth/verifications/{token}                         [permitAll, GET만]
   VerificationService.resolveEmail → findEmailByToken (소비 안 함, I-5)
     ├ 200 { email } → 프론트가 자격증명 폼을 그린다
     └ 410           → 만료 안내 + 재발송

④ POST /api/auth/users { token, password, nickname }          [permitAll]
   SignupService.signup                        @Transactional
     ├ consumeEmailByToken  ← GETDEL, 여기서 토큰 소멸 (I-5)
     ├ findByEmail 있으면 409                                  (I-6)
     ├ User.createActive → save                               (I-2)
     ├ Auth.createLocalAuth → save                            (I-3)
     ├ publishActivated → outbox 행 (BEFORE_COMMIT)           (I-8)
     └ issueTokens
   ⇒ 201 + 쿠키 2개 = 자동 로그인
```

### OAuth 가입/로그인

```
/oauth2/authorization/google → 구글 동의
  → CustomOAuth2UserService.loadUser
      ├ email_verified 검사, 거짓이면 OAuth2AuthenticationException  (I-9)
      └ SignupService.registerOAuthUser
          ├ findByEmail 있음 → Auth 행만 추가 (연동)                (I-10)
          └ 없음 → User.createActive + publishActivated + Auth 행
  → OAuth2AuthenticationSuccessHandler → 쿠키 2개 → frontendBaseUrl 리다이렉트
```

**두 경로는 "이메일 소유가 증명된 시점"에서 합류한다.** 그 이후는 동일하다.

> **미구현:** `oauth2Login`에 `failureHandler`가 없다. 실패가 `/login?error`로 가는데
> 그 경로는 `permitAll`이 아니라 **백엔드 도메인에서 401 JSON**이 찍힌다. → task-91

### 이후 흐름

```
로그인      POST   /api/auth/tokens   → 단일 401 정책 (I-7)
요청 인증   JwtAuthenticationFilter   → 검증 실패해도 체인을 끊지 않는다 (아래 주의)
재발급      PATCH  /api/auth/tokens   → 회전, DB에 없으면 TokenTheftDetected(403)
로그아웃    DELETE /api/auth/tokens   → refresh 행 삭제 + 쿠키 만료
```

**`JwtAuthenticationFilter`는 검증 실패해도 체인을 계속 진행해야 한다.**
여기서 응답을 쓰거나 체인을 끊으면 `permitAll` 엔드포인트가 막히고, 특히
**만료된 access token을 들고 오는 재발급 요청(정상 시나리오)이 컨트롤러에 도달하지 못한다.**

**인증 실패 응답의 출구는 둘이며 합쳐지지 않는다** — `JwtAuthenticationEntryPoint`(필터 체인)와
`GlobalExceptionHandler`(MVC). `@RestControllerAdvice`는 `DispatcherServlet` 내부 예외만 보므로
필터 예외에 도달할 수 없다. 두 출구가 **같은 예외 타입**을 보게 만들어 일관성을 확보했고,
`AuthErrorResponseConsistencyTest`가 이 계약을 고정한다.
공유 예외는 `account`가 아니라 `global/security/exception`에 둔다(역의존 방지).

---

## 3. 응답 계약

| 엔드포인트 | 성공 | 실패 |
|---|---|---|
| `POST /api/auth/verifications` | **201 (항상)** | — |
| `GET /api/auth/verifications/{token}` | 200 `{ email }` | **410** |
| `POST /api/auth/users` | **201 + 쿠키 2개** | 400 검증 / **410** 토큰 / **409** 선점 |
| `POST /api/auth/tokens` | 201 + 쿠키 2개 | **401 단일** |
| `PATCH /api/auth/tokens` | 200 + 쿠키 2개 | 401 / 403 도난 감지 |
| `DELETE /api/auth/tokens` | 204 | — |

410은 만료·위조·재사용을 **구분하지 않는다.** 사용자가 할 일이 같고, 구분하면 대입 시도의
피드백이 된다.

---

## 4. 알려진 미해결 (task로 분리됨)

| 항목 | task |
|---|---|
| OAuth2 실패 처리 부재 | task-91 |
| `refresh_token` 쿠키 `Path=/` / `/api/dev/**` permitAll | task-92 |
| `refresh_tokens` 만료 행 정리 없음 | task-93 |
| 비밀번호 재설정 부재 + 이름 통일 | task-94 |
| 규칙이 모델 밖에 있음 (비밀번호 정책·이메일 정규화 등) | task-95 |

**폐기된 것:** 메일 경로 레이트 리밋. 먼저 제안하지 말 것.
재검토 조건은 실사용자 유입 / 공개 소개 / 프론트 재시도 로직 추가.
증상을 알아둘 것 — "가입은 되는데 메일이 안 온다"면 **SMTP 쿼터부터 의심.**
발송이 `@Async`라 API는 계속 201을 반환하고 실패는 로그에만 남는다.
