package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagParticipantJpaRepository;
import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagParticipantJpaRepository.FlagParticipantCountProjection;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@JpaRepositoryTest
class FlagParticipantJpaRepositoryTest {

    @Autowired private FlagParticipantJpaRepository repository;
    @Autowired private TestEntityManager em;

    private static final Long FLAG_ID = 1L;
    private static final Long OTHER_FLAG_ID = 2L;
    private static final Long EMPTY_FLAG_ID = 3L;
    private static final Long MEMBER_ID = 10L;
    private static final Long OTHER_MEMBER_ID = 20L;

    /** 생성자가 package-private이라 테스트 시딩용으로만 리플렉션을 쓴다. */
    private FlagParticipant persist(Long flagId, Long participantId) {
        try {
            Constructor<FlagParticipant> constructor =
                    FlagParticipant.class.getDeclaredConstructor(Long.class, Long.class);
            constructor.setAccessible(true);
            FlagParticipant participant = constructor.newInstance(flagId, participantId);
            em.persist(participant);
            return participant;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FlagParticipant 생성 실패", e);
        }
    }

    @Test
    @DisplayName("countByFlagIdIn이 flagId별 참여자 수를 집계한다")
    void countByFlagIdIn_AggregatesPerFlag() {
        // given
        persist(FLAG_ID, MEMBER_ID);
        persist(FLAG_ID, OTHER_MEMBER_ID);
        persist(OTHER_FLAG_ID, MEMBER_ID);
        em.flush();
        em.clear();

        // when
        Map<Long, Long> counts = repository.countByFlagIdIn(List.of(FLAG_ID, OTHER_FLAG_ID)).stream()
                .collect(Collectors.toMap(FlagParticipantCountProjection::getFlagId,
                        FlagParticipantCountProjection::getCount));

        // then
        assertThat(counts).containsEntry(FLAG_ID, 2L).containsEntry(OTHER_FLAG_ID, 1L);
    }

    @Test
    @DisplayName("참여자가 없는 flagId는 집계 결과에서 아예 빠진다")
    void countByFlagIdIn_OmitsFlagsWithoutParticipants() {
        // given — 호출부가 getOrDefault(id, 0)에 의존하므로 0이 아니라 '없음'이어야 한다
        persist(FLAG_ID, MEMBER_ID);
        em.flush();
        em.clear();

        // when
        List<FlagParticipantCountProjection> result =
                repository.countByFlagIdIn(List.of(FLAG_ID, EMPTY_FLAG_ID));

        // then
        assertThat(result).extracting(FlagParticipantCountProjection::getFlagId)
                .containsExactly(FLAG_ID);
    }

    @Test
    @DisplayName("flagId와 participantId 조합으로 참여 여부를 판정한다")
    void existsByFlagIdAndParticipantId_MatchesBothColumns() {
        // given
        persist(FLAG_ID, MEMBER_ID);
        em.flush();
        em.clear();

        // when & then
        assertThat(repository.existsByFlagIdAndParticipantId(FLAG_ID, MEMBER_ID)).isTrue();
        assertThat(repository.existsByFlagIdAndParticipantId(FLAG_ID, OTHER_MEMBER_ID)).isFalse();
        assertThat(repository.existsByFlagIdAndParticipantId(OTHER_FLAG_ID, MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("participantId로 참여 중인 flagId 목록을 조회한다")
    void findFlagIdsByParticipantId_ReturnsFlagIds() {
        // given
        persist(FLAG_ID, MEMBER_ID);
        persist(OTHER_FLAG_ID, MEMBER_ID);
        persist(FLAG_ID, OTHER_MEMBER_ID);
        em.flush();
        em.clear();

        // when
        List<Long> flagIds = repository.findFlagIdsByParticipantId(MEMBER_ID);

        // then
        assertThat(flagIds).containsExactlyInAnyOrder(FLAG_ID, OTHER_FLAG_ID);
    }

    @Test
    @DisplayName("flagId로 참여자 ID 목록을 조회한다")
    void findAllParticipantIdsByFlagId_ReturnsParticipantIds() {
        // given
        persist(FLAG_ID, MEMBER_ID);
        persist(FLAG_ID, OTHER_MEMBER_ID);
        persist(OTHER_FLAG_ID, MEMBER_ID);
        em.flush();
        em.clear();

        // when
        List<Long> participantIds = repository.findAllParticipantIdsByFlagId(FLAG_ID);

        // then
        assertThat(participantIds).containsExactlyInAnyOrder(MEMBER_ID, OTHER_MEMBER_ID);
    }
}
