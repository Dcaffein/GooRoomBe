package com.example.DunbarHorizon.flag.domain.comment;

import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentAuthorizationException;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentInvalidContentException;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentReplyDepthException;
import com.example.DunbarHorizon.global.common.BaseTimeAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flag_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagComment extends BaseTimeAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long flagId;

    @Column(nullable = false)
    private Long writerId;

    private Long parentId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private boolean isPrivate;

    private FlagComment(Long flagId, Long writerId, String content, Long parentId, boolean isPrivate) {
        validateContent(content);
        this.flagId = flagId;
        this.writerId = writerId;
        this.content = content;
        this.parentId = parentId;
        this.isPrivate = isPrivate;
    }

    public static FlagComment createRoot(Long flagId, Long writerId, String content, boolean isPrivate) {
        return new FlagComment(flagId, writerId, content, null, isPrivate);
    }

    public FlagComment createReply(Long writerId, String content, boolean isPrivate) {
        validateAsParent();

        return new FlagComment(
                this.flagId,
                writerId,
                content,
                this.id,
                isPrivate
        );
    }

    public void update(Long requesterId, String content, boolean isPrivate) {
        validateWriter(requesterId);
        validateContent(content);
        this.content = content;
        this.isPrivate = isPrivate;
    }

    private void validateAsParent() {
        if (this.isReply()) {
            throw new FlagCommentReplyDepthException();
        }
    }

    public void validateDeletionAuthority(Long requesterId, Long hostId) {
        if (!this.writerId.equals(requesterId) && !hostId.equals(requesterId)) {
            throw new FlagCommentAuthorizationException("삭제 권한이 없습니다.");
        }
    }

    private void validateWriter(Long requesterId) {
        if (!this.writerId.equals(requesterId)) {
            throw new FlagCommentAuthorizationException("수정 권한은 작성자에게만 있습니다.");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > 500) {
            throw new FlagCommentInvalidContentException("내용은 1자 이상 500자 이하로 작성해주세요.");
        }
    }

    public boolean isWriter(Long userId) {
        return this.writerId.equals(userId);
    }

    public boolean isReply() {
        return this.parentId != null;
    }

    public boolean isVisibleTo(Long viewerId, Long hostId, Long parentWriterId) {
        if (!this.isPrivate) return true;

        if (viewerId == null) return false;

        return viewerId.equals(this.writerId)
                || viewerId.equals(hostId)
                || (isReply() && viewerId.equals(parentWriterId));
    }
}