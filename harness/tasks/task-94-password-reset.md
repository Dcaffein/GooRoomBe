# Task-94: 비밀번호 재설정

리팩토링이 아니라 **미구현 기능**이다. 다만 task-90의 직접적인 후속이고, 남겨두면 다른 결정들이
계속 이 부재에 걸린다.

## 배경

**현재 비밀번호를 잊은 사용자에게 복구 수단이 전혀 없다.** 계정에 접근할 방법이 사라진다.

이 부재가 다른 판단들을 왜곡하고 있다.

| 걸리는 지점 | 내용 |
|---|---|
| 인증 링크 선점 | 링크를 제3자가 먼저 클릭해 계정을 선점하면 회수할 방법이 없다. task-90에서 pending TTL을 24시간 → 1시간으로 줄인 근거 중 하나 |
| OAuth 자동 연동 | "메일함 통제 = 계정 접근"이 재설정이 있으면 서비스 전체의 전제가 되는데, 지금은 자동 연동만 그 등가성을 앞당겨 만든다 |
| 로컬 재가입 409 | 기존 계정이 있는 이메일로 재가입하면 409로 막는다. 그게 곧 "비밀번호 교체"이고 이번 범위 밖이라 막은 것이다. 재설정이 들어와야 이 비대칭이 정리된다 |

## 메커니즘은 task-90과 같다

**한계비용이 낮은 것이 이 task의 핵심 근거다.** 사전 인증 가입과 구조가 동일하다.

```
POST /api/auth/verifications  { email, purpose }   ← 접수 + 메일
  → Redis에 token → email 저장, TTL

메일 링크 클릭 → GET /api/auth/verifications/{token}   ← 유효성 확인 (소비 안 함)

폼 제출 → PATCH /api/auth/users/me/password { token, password }
  → GETDEL로 토큰 소비 → 비밀번호 교체
```

task-90 문서에서 `/verifications`라는 리소스명을 유지한 이유가 이것이다 —
**"이메일 소유 증명 절차"라는 범용 리소스**이고, 증명은 공용이며 소비처만 분기한다.

### `purpose` 필드가 이번에 필요해진다

task-90에서 "값이 하나뿐인 enum을 미리 만들지 않는다"는 이유로 뺐다. 이제 두 번째 값이 생긴다.

**분리하지 않으면 취약점이 된다.** 가입용으로 발급된 토큰이 비밀번호 재설정에 쓰이거나 그 반대가
가능해서는 안 된다. Redis 키 프리픽스를 분리(`account:signup:` / `account:password-reset:`)하거나
저장값에 purpose를 포함시켜 소비 시점에 대조한다. **키 분리 쪽이 단순하고 실수 여지가 적다.**

---

## 이름 통일 (이 task에 포함)

현재 **같은 데이터를 두 가지 이름으로 부르고 있다.**

| 요소 | 이름이 말하는 것 |
|---|---|
| `VerificationService` / `VerificationUseCase` | 증명 절차 |
| `POST /api/auth/verifications` | 증명 절차 |
| `PendingSignupRepository` | 가입 접수 |
| Redis 키 `account:signup:{token}` | 가입 접수 |

task-90 시점에는 소비처가 가입 하나뿐이라 드러나지 않았다. **이 task가 두 번째 소비처를
만들면서 `signup` 이름이 곧바로 틀린 이름이 된다** — 재설정용 토큰을 `PendingSignupRepository`에
담을 수는 없다.

그래서 이름 통일을 별도 task로 두지 않고 여기 포함한다. 따로 하면 같은 파일을 두 번 건드린다.

### 방향 — `signup`이 아니라 증명 쪽으로 통일한다

| 현재 | 변경 후 |
|---|---|
| `PendingSignupRepository` | `EmailOwnershipProofRepository` |
| `PendingSignupRedisAdapter` | `EmailOwnershipProofRedisAdapter` |
| `account:signup:{token}` | `account:proof:{purpose}:{token}` |
| `VerificationService` / `VerificationUseCase` | **유지** — 이제 이름이 실제 역할과 맞는다 |
| `SignupService` | **유지** — 증명의 소비처 중 하나 |

`SignupService`와 `VerificationService`의 분리축이 이 시점에 비로소 명확해진다:
**`VerificationService`는 증명을 발급·조회하고(공용), `SignupService`·재설정 서비스는 그 증명을
소비한다(소비처별).** task-90 직후에는 소비처가 하나여서 추측성 일반화였지만, 이 task가
두 번째 소비처를 만들면서 정당화된다.

### 주의

**Redis 키 형식이 바뀌므로 배포 시 구 키가 고아가 된다.** TTL 1시간이라 자연 소멸하지만,
**배포 직후 1시간 동안 발급된 가입 링크가 죽는다.** 트래픽이 적어 실질 영향은 없을 것이나
배포 노트에 남긴다. 정리 작업은 불필요하다.

**메서드명도 함께 본다.** `resolveEmail`은 "무엇의 이메일인지"가 드러나지 않는다.
`consumeEmailByToken` / `findEmailByToken`의 대비는 유지할 것 — 소비 여부가 이름에 드러나는 것이
이 포트에서 가장 중요한 성질이다(task-90에서 GET이 토큰을 소비하지 않아야 하는 이유).

## 필수 요구사항

### 재설정 성공 = `refresh_tokens` 전량 삭제

**이걸 빼먹으면 재설정의 의미가 없다.** 계정을 탈취당해 비밀번호를 바꿔도, 공격자가 이미 발급받은
refresh token으로 7일간 계속 접근할 수 있다.

`RefreshTokenRepository.deleteAllByUserId`가 **이미 존재한다**(토큰 도난 감지 경로에서 사용 중).
그대로 호출하면 된다.

주의: access token은 무효화 수단이 없어 최대 1시간(`JWT_ACCESS_EXPIRATION = 3600`) 남는다.
이는 수용하기로 이미 판단된 사항이다.

### 계정 열거 방지 — 두 purpose가 **같은 규칙**이어야 한다

`POST /api/auth/verifications`가 이미 **가입 여부와 무관하게 201**을 반환한다(task-90).
재설정 경로도 같은 규칙을 따른다 — 미가입 이메일이면 "가입된 계정이 없습니다" 메일을 보내거나
아무것도 보내지 않되, **응답은 동일**해야 한다.

**이건 취향 문제가 아니라 이 task가 만드는 구조적 제약이다.** 하나의 엔드포인트가 두 purpose를
받고 `purpose`는 **호출자가 정한다.** 따라서 한쪽 분기라도 존재 여부를 응답으로 노출하면
공격자는 그쪽 purpose만 골라 쓰면 된다 — **약한 분기가 엔드포인트 전체의 강도를 결정한다.**

즉 "가입 요청 시 이미 등록된 메일이면 409를 주자"는 선택은, 재설정 경로의 열거 방지까지
같이 무력화시킨다. 두 분기 모두 201이어야 한다.

## 결정 필요

| # | 항목 | 비고 |
|---|---|---|
| 1 | **OAuth 전용 계정에 재설정을 요청하면?** | 로컬 `Auth` 행이 없다. 새로 만들어 비밀번호를 부여할지(= 로컬 로그인 수단 추가), 거부할지. 거부하면 문구가 계정 열거 단서가 되지 않도록 주의 |
| 2 | **TTL** | 가입용 1시간과 맞출지. 재설정은 "메일함을 열어 클릭"이라는 같은 동작이므로 맞추는 쪽이 자연스럽다 |
| 3 | **재설정 후 자동 로그인 여부** | 가입은 201 + 쿠키로 자동 로그인한다. 재설정도 같게 할지, 로그인 화면으로 보낼지. 전량 삭제와의 순서 주의 — 삭제 후 발급이어야 한다 |
| 4 | **엔드포인트 경로** | `PATCH /api/auth/users/me/password`는 인증된 사용자의 비밀번호 변경과 이름이 겹친다. 미인증 상태에서 토큰으로 접근하는 경로이므로 구분이 필요할 수 있다 |

## 검증

- 재설정 후 **기존 세션이 전부 끊기는지** (다른 브라우저에서 로그인 상태가 유지되면 실패)
- 가입용 토큰으로 재설정이 **불가능한지**, 재설정 토큰으로 가입이 **불가능한지**
- 토큰 1회용 보장 — 같은 링크로 두 번 재설정 불가
- 미가입 이메일과 기가입 이메일의 **응답이 동일한지**
- 재설정 직후 구 비밀번호로 로그인이 **실패**하는지

## 관련

- task-90 — 동일 메커니즘. `PendingSignupRepository`, `VerificationService` 구조 재사용
- `RefreshTokenRepository.deleteAllByUserId` — 세션 무효화, 이미 존재
- `EmailAdapter` — 메일 템플릿 추가 지점

## Result

_미착수_
