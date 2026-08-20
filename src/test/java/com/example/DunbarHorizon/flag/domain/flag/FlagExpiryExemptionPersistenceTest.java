package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.adapter.out.persistence.FlagMemorialRepositoryAdapter;
import com.example.DunbarHorizon.flag.adapter.out.persistence.FlagRepositoryAdapter;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlagExpiryExemptionPolicy가 save() 없이 더티 체킹만으로 반영되는지 확인한다.
 * mock 테스트로는 잡히지 않는 부분이라 실제 DB로 검증한다.
 */
@JpaRepositoryTest
@Import({FlagExpiryExemptionPolicy.class, FlagRepositoryAdapter.class, FlagMemorialRepositoryAdapter.class})
class FlagExpiryExemptionPersistenceTest {

    @Autowired private FlagExpiryExemptionPolicy policy;
    @Autowired private TestEntityManager em;

    private static final Long HOST_ID = 1L;

    private Flag persistEndedFlag() {
        LocalDateTime past = LocalDateTime.now().minusDays(3);
        Flag flag = Flag.create(HOST_ID, "지난 플래그", "설명", 10,
                FlagSchedule.of(past, past.plusHours(1), past.plusHours(2)));
        em.persist(flag);
        return flag;
    }

    @Test
    @DisplayName("Encore가 생기면 save() 없이 auto_expiry_exempt가 반영된다")
    void refresh_EncoreExists_PersistsWithoutSave() {
        // given
        Flag parent = persistEndedFlag();
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        Flag encore = parent.createEncore(HOST_ID, future, future.plusHours(1), future.plusHours(2));
        em.persist(encore);
        em.flush();

        assertThat(parent.isAutoExpiryExempt()).isFalse();

        // when
        policy.refresh(parent.getId());
        em.flush();
        em.clear();

        // then
        Flag reloaded = em.find(Flag.class, parent.getId());
        assertThat(reloaded.isAutoExpiryExempt()).isTrue();
    }

    @Test
    @DisplayName("면제 조건이 사라지면 false로 되돌아간다")
    void refresh_NoExemptionSource_PersistsFalse() {
        // given
        Flag flag = persistEndedFlag();
        flag.updateAutoExpiryExempt(true);
        em.flush();
        em.clear();

        // when
        policy.refresh(flag.getId());
        em.flush();
        em.clear();

        // then
        Flag reloaded = em.find(Flag.class, flag.getId());
        assertThat(reloaded.isAutoExpiryExempt()).isFalse();
    }
}
