package com.example.DunbarHorizon.account.adapter.out.persistence;

import com.example.DunbarHorizon.account.adapter.out.persistence.jpa.UserJpaRepository;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.UserStatus;
import com.example.DunbarHorizon.account.domain.policy.EmailPolicy;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    /**
     * 조회 전에 정규화한다. 이 어댑터가 포트의 유일한 구현이라 모든 이메일 조회가 여기를
     * 지나므로, 호출자마다 정규화를 반복하지 않아도 쓰기(@code User.createActive})와
     * 같은 기준으로 맞춰진다.
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(EmailPolicy.normalize(email));
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public List<User> findActivatedUsers(Collection<Long> ids) {
        return userJpaRepository.findAllByIdInAndStatus(ids, UserStatus.ACTIVE);
    }

    @Override
    public Optional<User> findActivatedUser(Long id) {
        return userJpaRepository.findByIdAndStatus(id, UserStatus.ACTIVE);
    }

    @Override
    public void delete(User user) {
        userJpaRepository.delete(user);
    }

    @Override
    public void flush() {
        userJpaRepository.flush();
    }

}
