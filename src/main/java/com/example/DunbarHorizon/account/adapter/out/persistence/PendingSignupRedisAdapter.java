package com.example.DunbarHorizon.account.adapter.out.persistence;

import com.example.DunbarHorizon.account.domain.repository.PendingSignupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PendingSignupRedisAdapter implements PendingSignupRepository {

    private static final String KEY_PREFIX = "account:signup:";

    /**
     * 링크를 클릭한 사람이 비밀번호를 정하므로 이 토큰은 사실상 비밀번호 설정 권한이다.
     * 만료 시 사용자가 잃는 것은 이메일 재입력 한 번뿐이라 짧게 잡는다.
     */
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(String token, String email) {
        stringRedisTemplate.opsForValue().set(key(token), email, TTL);
    }

    @Override
    public Optional<String> findEmailByToken(String token) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(token)));
    }

    @Override
    public Optional<String> consumeEmailByToken(String token) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().getAndDelete(key(token)));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
