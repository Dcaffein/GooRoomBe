package com.example.DunbarHorizon.flag.adapter.out.persistence;

import com.example.DunbarHorizon.flag.adapter.out.persistence.jpa.FlagCommentJpaRepository;
import com.example.DunbarHorizon.flag.domain.comment.FlagComment;
import com.example.DunbarHorizon.support.JpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JpaRepositoryTest
class FlagCommentJpaRepositoryTest {

    @Autowired private FlagCommentJpaRepository repository;
    @Autowired private TestEntityManager em;

    private static final Long FLAG_ID = 1L;
    private static final Long OTHER_FLAG_ID = 2L;
    private static final Long WRITER_ID = 10L;

    private FlagComment persistRoot(Long flagId, String content) {
        FlagComment comment = FlagComment.createRoot(flagId, WRITER_ID, content, false);
        em.persist(comment);
        return comment;
    }

    private FlagComment persistReply(FlagComment parent, String content) {
        FlagComment reply = parent.createReply(WRITER_ID, content, false);
        em.persist(reply);
        return reply;
    }

    @Test
    @DisplayName("deleteTargetAndReplies가 대상 댓글과 그 답글을 함께 지운다")
    void deleteTargetAndReplies_RemovesTargetAndItsReplies() {
        // given
        FlagComment target = persistRoot(FLAG_ID, "지울 루트");
        em.flush();
        persistReply(target, "답글 1");
        persistReply(target, "답글 2");
        em.flush();
        em.clear();

        // when
        repository.deleteTargetAndReplies(target.getId());
        em.clear();

        // then
        assertThat(repository.findAllByFlagId(FLAG_ID)).isEmpty();
    }

    @Test
    @DisplayName("deleteTargetAndReplies가 다른 댓글은 남긴다")
    void deleteTargetAndReplies_KeepsOtherComments() {
        // given
        FlagComment target = persistRoot(FLAG_ID, "지울 루트");
        FlagComment survivor = persistRoot(FLAG_ID, "남을 루트");
        em.flush();
        persistReply(target, "지울 답글");
        FlagComment survivorReply = persistReply(survivor, "남을 답글");
        em.flush();
        em.clear();

        // when
        repository.deleteTargetAndReplies(target.getId());
        em.clear();

        // then
        assertThat(repository.findAllByFlagId(FLAG_ID))
                .extracting(FlagComment::getId)
                .containsExactlyInAnyOrder(survivor.getId(), survivorReply.getId());
    }

    @Test
    @DisplayName("flagId로 댓글 수를 센다")
    void countByFlagId_CountsRootsAndReplies() {
        // given
        FlagComment root = persistRoot(FLAG_ID, "루트");
        em.flush();
        persistReply(root, "답글");
        persistRoot(OTHER_FLAG_ID, "다른 플래그 댓글");
        em.flush();
        em.clear();

        // when & then
        assertThat(repository.countByFlagId(FLAG_ID)).isEqualTo(2);
        assertThat(repository.countByFlagId(OTHER_FLAG_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("hardDeleteByFlagIdsIn이 여러 Flag의 댓글을 한 번에 지운다")
    void hardDeleteByFlagIdsIn_RemovesByFlag() {
        // given
        persistRoot(FLAG_ID, "댓글 A");
        persistRoot(OTHER_FLAG_ID, "댓글 B");
        persistRoot(3L, "남을 댓글");
        em.flush();
        em.clear();

        // when
        repository.hardDeleteByFlagIdsIn(List.of(FLAG_ID, OTHER_FLAG_ID));
        em.clear();

        // then
        assertThat(repository.countByFlagId(FLAG_ID)).isZero();
        assertThat(repository.countByFlagId(OTHER_FLAG_ID)).isZero();
        assertThat(repository.countByFlagId(3L)).isEqualTo(1);
    }
}
