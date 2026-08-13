package com.example.DunbarHorizon.account.adapter.out.persistence;

import com.example.DunbarHorizon.account.adapter.out.persistence.jpa.UserJpaRepository;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.UserStatus;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@JpaRepositoryTest
class UserJpaRepositoryTest {

    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("ID와 활성화 상태가 모두 일치하는 유저를 조회한다")
    void findByIdAndStatus_Success() {
        // given
        User activeUser = User.builder()
                .email("active@test.com")
                .nickname("active")
                .status(UserStatus.ACTIVE)
                .build();
        User dormantUser = User.builder()
                .email("dormant@test.com")
                .nickname("dormant")
                .status(UserStatus.DORMANT)
                .build();

        em.persist(activeUser);
        em.persist(dormantUser);
        em.flush();

        // when
        Optional<User> result = userJpaRepository.findByIdAndStatus(activeUser.getId(), UserStatus.ACTIVE);
        Optional<User> resultFalse = userJpaRepository.findByIdAndStatus(dormantUser.getId(), UserStatus.ACTIVE);

        // then
        assertThat(result).isPresent();
        assertThat(resultFalse).isEmpty();
    }
}