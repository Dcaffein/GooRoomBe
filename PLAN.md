# PLAN — task-90: 로컬 회원가입 사전 인증 전환 및 로그인 응답 정규화

## 작업 목표

1. 로컬 회원가입을 **C-2 사전 인증**으로 전환한다.
   `이메일 접수 → 메일 → 링크 클릭(소유 증명) → 그 페이지에서 비밀번호·닉네임 입력 → 계정 생성`
2. 로컬/OAuth를 **단일 모델**로 통일한다. 생성된 계정은 경로 무관하게 항상 인증 완료 상태.
3. 로그인 실패 응답을 **단일 401**로 정규화하고 내부 PK 노출을 제거한다.

---

## 현황 분석

### 현재 플로우와 문제 위치

```
POST /api/auth/users {email, pw, nickname}
  └ SignupService:28  findByEmail → 없으면 생성 (dedup)
    SignupService:30  if (!user.isPending()) throw      ← ACTIVE만 보호
    SignupService:37  existingAuth.overwritePassword()  ← ★ 취약점
POST /api/auth/verifications {email}
  └ VerificationService:38  기존 토큰 삭제 → :41 새 토큰 → :43 발송
PATCH /api/auth/verifications?token=
  └ VerificationService:57  verify() + :58 activate()   ← 자격증명 미검증
```

`Auth.verified`가 유일한 방어선이고, 토큰은 `userId`만 담는다
(`EmailVerificationTokenRedisAdapter:23`). 클릭 시점에 그 행에 어떤 비밀번호가 있든 승인된다.

### 전환 후 죽는 코드

C-2에서는 미인증 Auth도 PENDING User도 생성되지 않으므로 아래가 전부 도달 불가가 된다.

| 대상 | 위치 |
|---|---|
| `Auth.verified` + `verify()` + `overwritePassword()` | `Auth.java:35,46,86` |
| `User.isPending()` / `overwritePendingProfile()` / `UserStatus.PENDING` | `User.java:61,71` |
| `User.activate()` | `User.java:86` — 호출부 2곳이 모두 사라짐. `deactivate()`는 애초에 호출부 없음 |
| `NotVerifiedException` + `LoginService:53-55` | — |
| `AccountCleanupService` / `Scheduler` / `EventListener` / `UseCase` | 4개 클래스 |
| `deleteOldUnverifiedAuths` / `deleteOldPendingUsers` / `deleteUnverifiedByUserId` | 각 3계층 |
| `EmailVerificationTokenRepository` + Redis 어댑터 | 대체됨 |

### 확인된 제약

- **`ddl-auto: update`** (`application.yml:37`) — Hibernate는 컬럼을 **삭제하지 않는다.**
  `Auth.verified`는 `@Column(nullable = false)`이므로 필드만 지우면 기존 NOT NULL 컬럼이 남아
  INSERT가 실패한다. 수동 DDL 필수.
- **`UserOutboxEventListener:35`는 `@TransactionalEventListener(BEFORE_COMMIT)`이다.**
  이벤트를 트랜잭션 **밖**에서 발행하면 리스너가 아예 실행되지 않는다.
  → outbox 행 없음 → Neo4j `SocialUser` 노드 없음.
- **`save()` 전에는 `user.getId()`가 null이다** (`@GeneratedValue`).
  생성 팩토리 안에서 `registerEvent`를 할 수 없다.
- `SecurityConfig:48`의 `"/api/auth/verifications"`는 **정확 매칭**이다. 하위 경로는 열리지 않는다.
- `EmailAdapter:26`은 `@Async`다. 발송 실패가 호출자에게 전파되지 않는다.
- `EmailAdapter:22` `app.frontend.base-url` + `redirectPage`로 링크를 만든다.

---

## 구현 방향

### 1. 엔드포인트 재설계

| Method | Path | Body / Param | 역할 |
|---|---|---|---|
| POST | `/api/auth/verifications` | `{email, redirectPage}` | **접수 + 메일 발송.** 계정 존재를 요구하지 않음 |
| GET | `/api/auth/verifications/{token}` | — | **(신규)** 토큰 유효성 확인. 폼 표시 전 호출 |
| POST | `/api/auth/users` | `{token, password, nickname}` | **계정 생성 + 쿠키 발급(자동 로그인)** |
| ~~PATCH~~ | ~~`/api/auth/verifications`~~ | | **삭제** — 역할이 `POST /users`로 흡수 |

**개념 재배치.** "인증메일 발송"과 "이메일 인증"이 독립 개념에서 사라지고,
"회원가입"이 1단계(이메일 접수)와 2단계(계정 생성)로 쪼개진다.
"재발송"은 별도 동작이 아니라 `POST /verifications` 재요청과 동일하므로 엔드포인트가 하나 줄어든다.

**`GET`이 필요한 이유**: 프론트가 폼을 그리기 전에 토큰 생존을 확인해야 한다.
없으면 사용자가 비밀번호까지 입력해 제출한 뒤에야 만료를 알게 된다.

**`/verifications` 이름을 유지하는 이유**: 이 리소스의 본질은 "이메일 소유 증명 절차"이며
회원가입 전용이 아니다. 후속 비밀번호 재설정 task에서 `{email, purpose}`로 확장해
증명은 공용, 소비처만 분기(`POST /users` vs `PATCH /users/me/password`)하는 형태가 된다.
**단 `purpose` 필드는 이번에 넣지 않는다** — 값이 하나뿐인 enum을 미리 만들지 않는다.

`POST /users` 성공 시 쿠키를 발급해 바로 로그인 상태로 만든다. 소유 증명과 비밀번호 설정을
방금 마친 사람이므로 안전하고, 2단계로 늘어난 가입 UX를 보상한다.

### 2. pending signup 저장 — Redis, 토큰 단위 키

```
account:signup:{token} → email      TTL 1시간
```

- **자격증명을 담지 않는다.** C-2의 핵심.
- **역방향 인덱스(이메일→토큰)를 두지 않는다 = dedup 없음.**

  > dedup을 걸면 저장량이 고유 이메일 수에 비례해 유리하지만, **공격자가 피해자 이메일로
  > 반복 접수해 피해자의 유효 토큰을 계속 무효화하는 DoS**가 생긴다. 레코드가 이메일과 토큰뿐이라
  > 건당 100바이트 남짓이므로 DoS를 피하는 쪽을 택한다. 총량 방어는 task-91이 담당한다.

- **TTL 1시간.** 기존 24시간에서 단축한다. 근거 두 가지:
  1. 만료 비용이 "이메일 한 번 재입력"으로 줄었다 (기존에는 PENDING 계정이 남아 재발송 요청).
  2. **C-2에서 링크의 민감도는 오히려 올라간다.** 클릭한 사람이 비밀번호를 정하므로 사실상
     비밀번호 설정 링크다. 메일 전달·공용 PC·메일함 노출 시 제3자가 그 이메일로 계정을
     선점할 수 있고, **이번 범위에 재설정 플로우가 없으므로 피해자에게 회수 수단이 없다.**
- **토큰은 1회용.** 계정 생성 성공 시 삭제한다.
- 기존 `EmailVerificationTokenRedisAdapter`는 `userId` 기반이라 재활용하지 않고 **대체**한다.
  프리픽스를 `account:email-verification:` → `account:signup:`으로 바꿔 배포 시 구 키와 섞이지 않게 한다.

### 3. 계정 생성과 이벤트 발행 순서

```java
@Transactional
public AuthTokenResult signup(String token, String password, String nickname) {
    String email = pendingSignupRepository.findEmailByToken(token)
            .orElseThrow(InvalidVerificationTokenException::new);

    User user = userRepository.save(User.createActive(email, nickname));  // ① id 확보
    authRepository.save(Auth.createLocalAuth(user.getId(), hasher.encode(password))); // ②
    eventPublisher.publishEvent(new UserActivatedEvent(                   // ③ 트랜잭션 안
            user.getId(), user.getNickname(), user.getProfileImage()));
    pendingSignupRepository.delete(token);                                // ④ 1회용 보장

    return issueTokens(user);
}
```

- ①이 먼저인 이유: `save()` 전에는 `getId()`가 null
- ③이 트랜잭션 **안**이어야 하는 이유: `UserOutboxEventListener:35`가 `BEFORE_COMMIT`이라
  밖에서 발행하면 실행되지 않는다. outbox 행이 유저 행과 같은 트랜잭션으로 커밋되어야 원자적이다.
- **이벤트 발행 경로가 하나로 통일된다.** 현재는 `User.activate()`의 `registerEvent`(@DomainEvents)와
  `SignupService:52`/`DevUserService:45`의 `eventPublisher` 직접 호출이 공존한다.
  `activate()`가 죽으면서 **직접 호출 하나만 남는다.**
  (두 갈래였던 것이 커밋 `a244f8d`의 "`save()` 누락 → `@DomainEvents` 미발행" 버그를 낳은 구조다.)

### 4. 계정 열거 대응 — 응답은 통일, 메일 내용으로 분기

`POST /verifications`는 이메일 등록 여부와 무관하게 **항상 201**을 반환한다.

| 상황 | 응답 | 메일 |
|---|---|---|
| 미가입 | 201 | 가입 계속하기 링크 |
| 이미 가입됨 | 201 | "이미 가입된 계정입니다. 로그인하세요" (링크 없음) |

응답만으로는 가입 여부를 알 수 없고, **실제 이메일 주인은 여전히 상황을 안다.** UX 손실이 없다.
`AlreadyRegisteredEmailException`은 이 경로에서 사라진다.

### 5. 로그인 정규화

```java
// 신규: account/domain/exception/InvalidCredentialsException (401)
// 메시지는 생성자에서 고정 — 호출부가 주입할 수 없게 한다
User user = userRepository.findByEmail(email)
        .orElseThrow(InvalidCredentialsException::new);
Auth localAuth = authRepository.findByUserIdAndProvider(user.getId(), LOCAL)
        .orElseThrow(InvalidCredentialsException::new);
if (!passwordHasher.matches(password, localAuth.getPassword())) {
    throw new InvalidCredentialsException();
}
```

- 실패 사유는 `log.warn`으로만 남긴다.
- `BadCredentialsException`(Spring Security) 의존 제거 → **계층 위반과 500을 동시에 해소.**
- `NotVerifiedException` 분기(`:53-55`) 삭제.

### 6. OAuth 경로

- `registerOAuthUser`에서 `user.isPending()` 분기 제거. PENDING User가 존재하지 않는다.
- **`email_verified` 클레임 검증.** 구글이 미인증이라고 응답한 이메일로는 기존 계정에 연동하지 않는다.
  "이메일 문자열 일치만으로 병합하지 않는다"의 실질 구현.

### 7. SecurityConfig

`"/api/auth/verifications"` 정확 매칭에 **`GET /api/auth/verifications/*`만 좁게** 추가한다.
`/**`로 넓히면 이후 이 경로 아래 추가되는 엔드포인트가 자동으로 `permitAll`이 된다.

---

## 확정 사항

| # | 항목 | 결정 |
|---|---|---|
| 1 | 비밀번호 재설정 플로우 | **이번 범위 제외** — 별도 task로 즉시 후속 |
| 2 | pending 저장소 TTL | **1시간** |
| 3 | 이메일 기준 dedup | **없음** (토큰 단위 키) |
| 4 | `Auth.verified` 컬럼 | **DROP** (수동 DDL) |
| 5 | `UserActivatedEvent` 발행 시점 | **계정 생성 성공 시점**, `save()` 직후, 트랜잭션 안 |
| 6 | `/verifications` 리소스명 | **유지** (증명 절차의 범용 리소스), `purpose` 필드는 후속 |

> **1번 주의**: 제외해도 기능 후퇴는 아니다(현재도 재설정 기능 없음). 다만 비밀번호를 잊은
> 사용자의 복구 수단이 계속 없는 상태이고, TTL 1시간 근거에서 언급한 "선점 시 회수 불가"도
> 재설정이 들어오면 해소된다. **곧바로 다음 task로 이어갈 것.**

---

## 변경 파일 목록

### 신규

| 파일 | 내용 |
|---|---|
| `account/application/port/out/PendingSignupRepository.java` | `save(token, email)` / `findEmailByToken` / `delete(token)` |
| `account/adapter/out/persistence/PendingSignupRedisAdapter.java` | 위 포트의 Redis 구현, TTL 1시간 |
| `account/domain/exception/InvalidCredentialsException.java` | 401, 메시지 생성자 고정 |
| `account/domain/exception/InvalidVerificationTokenException.java` | 400/404, 만료·위조 토큰 |
| `account/adapter/in/web/dto/SignupCompleteRequestDto.java` | `{token, password, nickname}` |
| `account/adapter/in/web/dto/VerificationTokenResponse.java` | `{email}` — GET 응답 |

### 수정

| 파일 | 내용 |
|---|---|
| `AccountController` | `POST /users` 시그니처 변경 + 쿠키 발급, `GET /verifications/{token}` 추가, `PATCH /verifications` 삭제 |
| `SignupUseCase` / `SignupService` | `signup(token, password, nickname)`, `registerOAuthUser`에서 PENDING 분기 제거 |
| `VerificationUseCase` / `VerificationService` | `sendVerificationEmail` 재작성(계정 불요), `validateToken` 추가, `verifyEmail` 삭제 |
| `LoginService` | 401 통합, `NotVerifiedException` 분기 삭제, `BadCredentialsException` 제거 |
| `Auth` | `verified`·`verify()`·`overwritePassword()` 삭제, `createLocalAuth`를 유일 생성 경로로 |
| `User` | `isPending()`·`overwritePendingProfile()`·`activate()` 삭제, `UserStatus.PENDING` 제거, `createActive()` 추가 |
| `SecurityConfig:48` | `GET /api/auth/verifications/*` 좁게 추가 |
| `EmailPort` / `EmailAdapter` | 이미 가입된 계정용 메일 템플릿 추가 |
| `GoogleOAuth2UserInfo` / `CustomOAuth2UserService` | `email_verified` 검증 |
| `AuthRepository` / `UserRepository` + JPA + 어댑터 | cleanup 관련 메서드 3종 삭제 |
| `DevUserService:45` | `User.createActive()` 사용으로 정렬 (동작 동일) |

### 삭제

`AccountCleanupService` · `AccountCleanupScheduler` · `AccountCleanupEventListener` ·
`AccountCleanupUseCase` · `NotVerifiedException` · `EmailVerificationTokenRepository` ·
`EmailVerificationTokenRedisAdapter` · 대응 테스트 파일

---

## 배포 시 사용자가 직접 실행할 작업

에이전트가 대신할 수 없는 항목이다.

**1. `verified` 컬럼 DROP** — 배포 **전** 실행. 누락 시 모든 회원가입이 실패한다.
```sql
ALTER TABLE auths DROP COLUMN verified;
```
`users.status`는 문자열 컬럼이라 `PENDING` enum 값만 빠지면 되므로 DDL 불필요.

**2. 기존 PENDING 데이터 일회성 정리** — `AccountCleanupService`를 제거하므로 자연 소멸을 기대할 수 없다.
```sql
DELETE FROM auths WHERE user_id IN (SELECT user_id FROM users WHERE status = 'PENDING');
DELETE FROM users WHERE status = 'PENDING';
```
Redis 구 키 `account:email-verification:*`도 함께 정리.

**3. 프론트엔드 2단계 전환** — 이메일 입력 화면, 메일 링크 착지 페이지(폼) 신설,
`redirectPage` 값 변경, `PATCH /verifications` 호출 제거.

---

## 예상 사이드 이펙트

1. **Neo4j `SocialUser` 동기화 회귀 위험 (최우선)** — 이벤트 발행 시점이 바뀐다.
   `UserOutboxEventListener:35`가 `BEFORE_COMMIT`이라 트랜잭션 밖 발행 시 조용히 누락된다.
   증상은 "가입·로그인은 되는데 소셜 그래프에 노드가 없는 계정"이며 **에러가 나지 않아 늦게 발견된다.**
   통합 테스트로 고정한다.
2. **`ShedLock`에서 계정 정리 잡이 사라진다.** 스케줄러 등록 목록 확인 필요.
3. **task-91 간격.** `POST /verifications`가 계정 존재 검사 없이 발송하게 되어,
   현재 2회 요청으로 가능하던 남용이 1회로 줄어든다. 레이트 리밋을 짧은 간격으로 이어 붙인다.
4. **`AlreadyRegisteredEmailException`** 은 OAuth 경로 등 다른 사용처가 남아 있는지 확인 후 판단.

---

## 테스트 전략

`TESTING-GUIDE.md` 기본 프로토콜을 따른다. 아래는 반드시 포함할 케이스다.

**단위 (Mockito)**
- `SignupService`: 유효 토큰으로 계정 생성 / 만료·위조 토큰 거부 / **토큰 재사용 거부**
- `VerificationService`: 미가입·기가입 양쪽 모두 **동일하게 201**
- `LoginService`: 세 실패 경로가 **모두 같은 예외·같은 메시지**

**통합 (`BaseControllerTest`)**
- **회귀 방지 핵심 1** — 접수 → 토큰 A → **다른 자격증명으로 재접수** → 토큰 B →
  **토큰 A로 계정 생성 시 A 요청자가 정한 비밀번호가 적용되는지.**
  두 시도가 서로를 오염시키지 않음을 고정한다.
- **회귀 방지 핵심 2** — 로컬 가입 후 **outbox 행이 생성되는지** (Neo4j sync 경로 보증)
- 로그인 실패 3경우의 응답 본문이 **바이트 단위로 동일**한지
- `GET /verifications/{token}`이 `permitAll`인지, 그리고 **그 아래 다른 경로는 열리지 않는지**

**삭제할 테스트**
`AccountCleanupEventListenerTest` 등 제거 대상 클래스의 테스트. 삭제 전
"미인증 Auth 정리" 의도가 다른 테스트에 남아 있지 않은지 확인한다.

---

## 승인 대기

위 확정 사항 6개와 변경 파일 목록에 이견이 없으면 승인해 주기 바란다.
승인 시 `main`에서 `ai/refactor-signup-pre-verification` 브랜치를 생성하고 구현에 들어간다.
