package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagMemorialJpaRepository;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JpaRepositoryTest
class FlagMemorialJpaRepositoryTest {

    @Autowired private FlagMemorialJpaRepository repository;
    @Autowired private TestEntityManager em;

    private static final Long FLAG_ID = 1L;
    private static final Long OTHER_FLAG_ID = 2L;
    private static final Long WRITER_ID = 10L;
    private static final Long OTHER_WRITER_ID = 20L;

    /** 생성자가 package-private이라 테스트 시딩용으로만 리플렉션을 쓴다. */
    private FlagMemorial persist(Long flagId, Long writerId, String content) {
        try {
            Constructor<FlagMemorial> constructor =
                    FlagMemorial.class.getDeclaredConstructor(Long.class, Long.class, String.class);
            constructor.setAccessible(true);
            FlagMemorial memorial = constructor.newInstance(flagId, writerId, content);
            em.persist(memorial);
            return memorial;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FlagMemorial 생성 실패", e);
        }
    }

    @Test
    @DisplayName("flagId로 존재 여부와 개수를 조회한다")
    void existsAndCountByFlagId() {
        // given
        persist(FLAG_ID, WRITER_ID, "후기 1");
        persist(FLAG_ID, OTHER_WRITER_ID, "후기 2");
        em.flush();
        em.clear();

        // when & then
        assertThat(repository.existsByFlagId(FLAG_ID)).isTrue();
        assertThat(repository.existsByFlagId(OTHER_FLAG_ID)).isFalse();
        assertThat(repository.countByFlagId(FLAG_ID)).isEqualTo(2);
        assertThat(repository.countByFlagId(OTHER_FLAG_ID)).isZero();
    }

    @Test
    @DisplayName("flagId로 후기 목록을 조회한다")
    void findAllByFlagId_FiltersByFlag() {
        // given
        persist(FLAG_ID, WRITER_ID, "후기 1");
        persist(FLAG_ID, OTHER_WRITER_ID, "후기 2");
        persist(OTHER_FLAG_ID, WRITER_ID, "다른 플래그 후기");
        em.flush();
        em.clear();

        // when
        List<FlagMemorial> memorials = repository.findAllByFlagId(FLAG_ID);

        // then
        assertThat(memorials).hasSize(2)
                .extracting(FlagMemorial::getFlagId).containsOnly(FLAG_ID);
    }

    @Test
    @DisplayName("hardDeleteByFlagIdsIn이 여러 Flag의 후기를 한 번에 지운다")
    void hardDeleteByFlagIdsIn_RemovesByFlag() {
        // given
        persist(FLAG_ID, WRITER_ID, "후기 A");
        persist(OTHER_FLAG_ID, WRITER_ID, "후기 B");
        persist(3L, WRITER_ID, "남을 후기");
        em.flush();
        em.clear();

        // when
        repository.hardDeleteByFlagIdsIn(List.of(FLAG_ID, OTHER_FLAG_ID));
        em.clear();

        // then
        assertThat(repository.existsByFlagId(FLAG_ID)).isFalse();
        assertThat(repository.existsByFlagId(OTHER_FLAG_ID)).isFalse();
        assertThat(repository.existsByFlagId(3L)).isTrue();
    }
}
