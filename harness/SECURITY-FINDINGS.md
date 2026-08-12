# Security Findings Log

대화/리뷰 중 발견된 보안 관련 이슈를 누적 기록하는 문서. 수정 여부와 무관하게 발견 시점의 사실을 남긴다.
실제 수정 작업으로 옮길 때는 `harness/TASK-SPEC-GUIDE.md` 형식으로 `harness/tasks/`에 별도 task spec을 만든다.

## 기록 형식

| 필드 | 설명 |
|------|------|
| 발견일 | 발견한 날짜 |
| 심각도 | Critical / High / Medium / Low / Info |
| 위치 | 파일 경로:라인 |
| 상태 | Open / Fixed / Accepted Risk |

---

## F-001: 로그인 엔드포인트 계정 열거(User Enumeration) 취약점

- **발견일**: 2026-08-10
- **심각도**: High
- **위치**: `src/main/java/com/example/DunbarHorizon/account/application/service/LoginService.java:44-52`
- **상태**: Open

### 내용

`LoginService.login()`이 "존재하지 않는 이메일"과 "이메일은 존재하나 비밀번호 불일치"를 서로 다른 예외로 처리한다.

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));  // BusinessException → 404

Auth localAuth = authRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
        .orElseThrow(() -> new BadCredentialsException("이메일/비밀번호를 확인해주세요."));

if (!passwordHasher.matches(password, localAuth.getPassword())) {
    throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
}
```

`BadCredentialsException`(Spring Security 예외)은 `BusinessException`을 상속하지 않고, `GlobalExceptionHandler`에 전용 핸들러도 없다. 프로젝트 전체에서 이 예외를 던지는 곳도 `LoginService`뿐이라, 결국 catch-all `@ExceptionHandler(Exception.class)`로 떨어져 **500**으로 응답된다.

| 상황 | 응답 코드 | 응답 메시지 |
|---|---|---|
| 가입되지 않은 이메일 | `404` | "사용자를 찾을 수 없습니다." |
| 가입된 이메일 + 틀린 비밀번호 | `500` | "서버 내부 오류가 발생했습니다." |

### 영향

- 공격자가 `POST /api/auth/tokens`에 임의의 이메일을 대입해 응답 코드(404 vs 500)만으로 **가입 여부를 판별**할 수 있음 (계정 열거 공격의 기반이 됨 → 크리덴셜 스터핑/피싱 타겟 확정에 악용 가능).
- 부수 효과: 정상적인 비밀번호 오타까지 `log.error`로 스택트레이스가 찍혀 실제 장애가 아닌데도 에러 로그/알림이 오염됨.

### 권장 조치

"사용자 없음"과 "비밀번호 불일치"를 하나의 인증 실패 예외(예: `InvalidCredentialsException`, 401)로 통합하고, 두 경우 모두 **동일한 상태 코드·동일한 메시지**("이메일 또는 비밀번호가 일치하지 않습니다")로 응답한다. 인증/계정 존재 여부가 걸린 엔드포인트에서는 도메인 예외를 세분화하지 않고 의도적으로 뭉개는 것이 맞는 방향.

---

## F-002: 회원가입 이메일 중복 확인을 통한 계정 열거

- **발견일**: 2026-08-10
- **심각도**: Low (Accepted Risk 후보)
- **위치**: `src/main/java/com/example/DunbarHorizon/account/domain/Auth.java:48`, `AlreadyRegisteredEmailException` (409 CONFLICT)
- **상태**: Open (정책 판단 필요)

### 내용

`POST /api/auth/users`(회원가입)에서 이미 등록된 이메일이면 `409 "이미 인증된 이메일입니다 : {email}"`을 반환한다. 이 자체로 해당 이메일의 가입 여부를 제3자가 확인할 수 있다.

### 영향

F-001만큼 치명적이지 않음. 다수의 실서비스가 회원가입 UX(중복 안내)를 위해 감수하는 트레이드오프이나, 완전한 계정 열거 방지가 필요하다면 응답을 가입 여부와 무관하게 통일하고(예: "가입 확인 메일을 발송했습니다" 고정 메시지 + 이메일 인증 단계에서 실제 중복 처리) 실제 판단은 이메일 발송 여부로 대체하는 설계도 가능.

### 권장 조치

정책 결정 필요 — 계정 열거를 얼마나 엄격히 막을지는 제품 요구사항에 달림. 현재는 리스크를 인지한 상태로 유지(Accepted Risk)하거나, 이메일 인증 플로우 개편 시 함께 재검토.

---

## F-003: 로그아웃 DELETE 요청의 RequestBody 사용

- **발견일**: 2026-08-10
- **심각도**: Info
- **위치**: `src/main/java/com/example/DunbarHorizon/account/adapter/in/web/AccountController.java:50-59`
- **상태**: Open

### 내용

```java
@DeleteMapping("/tokens")
public ResponseEntity<Void> logout(
        @CookieValue(name = "refresh_token", required = false) String refreshToken,
        @RequestBody(required = false) LogoutRequest logoutRequest,
        HttpServletResponse response)
```

`DELETE` + `@RequestBody`(fcmToken 전달용) 조합은 HTTP 스펙상 허용되지만 비표준적이라, 일부 프록시/로드밸런서/CDN이 DELETE 요청의 body를 제거하는 경우가 있다. 이 경우 `fcmToken`이 서버에 도달하지 못해 디바이스 토큰 해제 로직이 조용히 스킵될 수 있음.

### 영향

보안 취약점이라기보다 신뢰성 이슈에 가까움. 다만 로그아웃 시 FCM 토큰이 해제되지 않으면 탈취된 세션에 계속 푸시가 전달되는 등 부차적 보안 영향 가능.

### 권장 조치

`fcmToken`을 쿼리 파라미터나 별도 헤더로 이전하거나, `POST /api/auth/tokens/revoke` 같은 액션형 엔드포인트로 전환.

---

## 참고: 설계 원칙 정리 (2026-08-10 대화 결론)

- 예상 가능한 도메인 규칙 위반(없음/충돌/권한없음 등)은 세분화된 상태 코드로 응답하는 것이 맞다. 전부 500으로 뭉개는 것은 보안 이득 없이 클라이언트 UX만 해친다.
- 예외적으로, **응답 차이 자체가 공격 표면이 되는 엔드포인트**(로그인, 계정 존재 확인 등)에서는 의도적으로 도메인 예외를 하나로 합쳐 동일한 응답을 주는 것이 맞다. F-001이 정확히 이 사례.
- 500 catch-all에서 내부 예외 클래스명/스택트레이스를 노출하지 않는 현재 `GlobalExceptionHandler` 구조는 유지할 것.
