package com.example.DunbarHorizon.flag.domain.comment;

import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentInvalidContentException;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentAuthorizationException;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentReplyDepthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class FlagCommentTest {

    private static final Long FLAG_ID = 1L;
    private static final Long WRITER_ID = 2L;
    private static final Long HOST_ID = 3L;
    private static final Long STRANGER_ID = 99L;

    @Test
    @DisplayName("루트 댓글을 생성할 수 있다")
    void createRoot_Success() {
        // when
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        // then
        assertThat(comment.getFlagId()).isEqualTo(FLAG_ID);
        assertThat(comment.getWriterId()).isEqualTo(WRITER_ID);
        assertThat(comment.getContent()).isEqualTo("내용");
        assertThat(comment.isReply()).isFalse();
    }

    @Test
    @DisplayName("내용이 비어있으면 댓글 생성 시 예외가 발생한다")
    void createRoot_BlankContent_ThrowsException() {
        assertThatThrownBy(() -> FlagComment.createRoot(FLAG_ID, WRITER_ID, "  ", false))
                .isInstanceOf(FlagCommentInvalidContentException.class);
    }

    @Test
    @DisplayName("500자를 초과하는 내용으로 댓글을 생성하면 예외가 발생한다")
    void createRoot_TooLongContent_ThrowsException() {
        // given
        String tooLong = "a".repeat(501);

        // when / then
        assertThatThrownBy(() -> FlagComment.createRoot(FLAG_ID, WRITER_ID, tooLong, false))
                .isInstanceOf(FlagCommentInvalidContentException.class);
    }

    @Test
    @DisplayName("루트 댓글에 답글을 생성할 수 있다")
    void createReply_Success() {
        // given: root comment must have an id for reply.parentId to be non-null
        FlagComment root = FlagComment.createRoot(FLAG_ID, WRITER_ID, "루트 댓글", false);
        ReflectionTestUtils.setField(root, "id", 1L);

        // when
        FlagComment reply = root.createReply(HOST_ID, "답글 내용", false);

        // then
        assertThat(reply.isReply()).isTrue();
        assertThat(reply.getWriterId()).isEqualTo(HOST_ID);
        assertThat(reply.getFlagId()).isEqualTo(FLAG_ID);
    }

    @Test
    @DisplayName("답글에 대한 답글(2단 중첩)을 생성하면 FlagCommentReplyDepthException이 발생한다")
    void createReply_ToReply_ThrowsException() {
        // given: reply needs parentId set so isReply() returns true
        FlagComment root = FlagComment.createRoot(FLAG_ID, WRITER_ID, "루트", false);
        ReflectionTestUtils.setField(root, "id", 1L);
        FlagComment reply = root.createReply(HOST_ID, "1단 답글", false);
        ReflectionTestUtils.setField(reply, "id", 2L);

        // when / then
        assertThatThrownBy(() -> reply.createReply(STRANGER_ID, "2단 답글", false))
                .isInstanceOf(FlagCommentReplyDepthException.class);
    }

    @Test
    @DisplayName("작성자가 댓글 내용과 공개 여부를 수정할 수 있다")
    void update_ByWriter_Success() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "원래 내용", false);

        // when
        comment.update(WRITER_ID, "수정된 내용", true);

        // then
        assertThat(comment.getContent()).isEqualTo("수정된 내용");
        assertThat(comment.isPrivate()).isTrue();
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 댓글을 수정하면 FlagCommentAuthorizationException이 발생한다")
    void update_ByNonWriter_ThrowsException() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        // when / then
        assertThatThrownBy(() -> comment.update(STRANGER_ID, "수정 시도", false))
                .isInstanceOf(FlagCommentAuthorizationException.class);
    }

    @Test
    @DisplayName("작성자는 삭제 권한 검증을 통과할 수 있다")
    void validateDeletionAuthority_ByWriter_Success() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        // when / then
        assertThatNoException().isThrownBy(() -> comment.validateDeletionAuthority(WRITER_ID, HOST_ID));
    }

    @Test
    @DisplayName("호스트는 다른 사람의 댓글에 대한 삭제 권한 검증을 통과할 수 있다")
    void validateDeletionAuthority_ByHost_Success() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        // when / then
        assertThatNoException().isThrownBy(() -> comment.validateDeletionAuthority(HOST_ID, HOST_ID));
    }

    @Test
    @DisplayName("작성자와 호스트가 아닌 사용자가 삭제 권한을 검증하면 예외가 발생한다")
    void validateDeletionAuthority_ByStranger_ThrowsException() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        // when / then
        assertThatThrownBy(() -> comment.validateDeletionAuthority(STRANGER_ID, HOST_ID))
                .isInstanceOf(FlagCommentAuthorizationException.class);
    }

    @Test
    @DisplayName("공개 댓글은 누구에게나 보인다")
    void isVisibleTo_PublicComment_VisibleToAll() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "공개 댓글", false);

        // when / then
        assertThat(comment.isVisibleTo(STRANGER_ID, HOST_ID, null)).isTrue();
        assertThat(comment.isVisibleTo(null, HOST_ID, null)).isTrue();
    }

    @Test
    @DisplayName("비공개 댓글은 작성자에게만 보인다")
    void isVisibleTo_PrivateComment_VisibleToWriter() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "비공개 댓글", true);

        // when / then
        assertThat(comment.isVisibleTo(WRITER_ID, HOST_ID, null)).isTrue();
    }

    @Test
    @DisplayName("비공개 댓글은 플래그 호스트에게 보인다")
    void isVisibleTo_PrivateComment_VisibleToHost() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "비공개 댓글", true);

        // when / then
        assertThat(comment.isVisibleTo(HOST_ID, HOST_ID, null)).isTrue();
    }

    @Test
    @DisplayName("비공개 댓글은 제3자에게 보이지 않는다")
    void isVisibleTo_PrivateComment_NotVisibleToStranger() {
        // given
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "비공개 댓글", true);

        // when / then
        assertThat(comment.isVisibleTo(STRANGER_ID, HOST_ID, null)).isFalse();
    }

    @Test
    @DisplayName("writerId와 같은 userId이면 isWriter가 true이다")
    void isWriter_WithWriterId_ReturnsTrue() {
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        assertThat(comment.isWriter(WRITER_ID)).isTrue();
    }

    @Test
    @DisplayName("writerId와 다른 userId이면 isWriter가 false이다")
    void isWriter_WithOtherId_ReturnsFalse() {
        FlagComment comment = FlagComment.createRoot(FLAG_ID, WRITER_ID, "내용", false);

        assertThat(comment.isWriter(STRANGER_ID)).isFalse();
    }
}
