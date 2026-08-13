# Task-93: `refresh_tokens` 만료 행 정리 스케줄러

## 배경

**`refresh_tokens` 테이블이 무한히 증가한다.** 만료된 행을 지우는 주체가 없다.

행이 지워지는 경로는 둘뿐이다.

| 경로 | 메서드 |
|---|---|
| 로그아웃 | `deleteByTokenValue` |
| 토큰 도난 감지 | `deleteAllByUserId` |

반면 **`issueTokens`는 로그인할 때마다 새 행을 만든다**(`LoginService:76-82`). 재발급은
`rotateTokenValue`로 기존 행을 갱신하므로 늘지 않지만, **로그아웃하지 않고 브라우저를 닫는
일반적인 사용 패턴에서는 행이 그대로 남는다.** 기기를 바꾸거나 시크릿 창을 쓸 때마다 하나씩 쌓인다.

task-90에서 `AccountCleanupScheduler`를 제거했지만 그건 PENDING 계정용이었고
`refresh_tokens`와는 무관했다. 즉 **이 테이블은 처음부터 정리 주체가 없었다.**

현재 트래픽에서 당장 문제가 되지는 않는다. 다만 방치하면 조용히 커지고, `tokenValue`가
`unique` 인덱스라 인덱스도 함께 커진다.

## 작업 범위

`expiryDate`가 지난 행을 주기적으로 삭제한다.

### 신규

| 파일 | 내용 |
|---|---|
| `account/adapter/in/scheduler/RefreshTokenCleanupScheduler.java` | `@Scheduled` + `@SchedulerLock` |
| 정리 서비스 | 기존 `UserOutboxCleanupService` 형태를 따른다 |

### 수정

| 파일 | 내용 |
|---|---|
| `RefreshTokenJpaRepository` | `deleteByExpiryDateBefore` 계열 `@Modifying` 쿼리 추가 |
| `RefreshTokenRepository` (포트) | 대응 메서드 추가 |
| `RefreshTokenRepositoryAdapter` | 위임 |

기존 패턴을 그대로 재사용할 수 있다 — `UserOutboxCleanupScheduler`가 참고 대상이다.

```java
@Scheduled(cron = "0 0 3 * * *")
@SchedulerLock(name = "userOutboxCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
```

## 구현 시 주의 — **이게 이 task의 핵심이다**

### `expiryDate`는 UTC로 저장된다

`LoginService:74`가 `authTokenProvider.getExpirationTime()`으로 JWT의 `exp`를 그대로 꺼내 넣는다.
즉 **UTC 기준값**이다.

**컨테이너 타임존은 KST이므로 `LocalDateTime.now()`를 쓰면 9시간 어긋난다.**
그러면 아직 9시간 남은 유효한 토큰까지 지워서 **사용자가 이유 없이 로그아웃된다.**

```java
// 반드시 이렇게
LocalDateTime.now(ZoneOffset.UTC)
```

**task-89에서 삭제한 `RefreshToken.isExpired()`가 정확히 이 버그였다.**
같은 실수를 스케줄러에서 반복하기 쉬운 구조다.

### 삭제 배치 크기

행이 많이 쌓인 상태에서 첫 실행이 대량 삭제를 하면 락이 길어진다.
`lockAtMostFor`를 넉넉히 잡거나 배치 삭제를 고려한다.

## 검증

- 만료된 행만 삭제되고 **유효한 행은 남는지**
- **UTC 경계 테스트가 필수다.** 만료까지 1시간 남은 행을 만들고 스케줄러를 돌렸을 때
  살아남는지 확인한다. KST로 계산하면 이 케이스가 지워진다
- 삭제 후 해당 사용자의 재발급이 정상 동작하는지

## 관련

- `LoginService:69-85` — `issueTokens`, 행 생성 지점
- `LoginService:74` — `expiryDate`의 UTC 출처
- `UserOutboxCleanupScheduler` — 패턴 참고
- task-89 — 같은 UTC 버그의 선례

## Result

_미착수_
