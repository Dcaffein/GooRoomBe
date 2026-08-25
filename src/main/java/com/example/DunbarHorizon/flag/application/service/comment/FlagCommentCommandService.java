package com.example.DunbarHorizon.flag.application.service.comment;

import com.example.DunbarHorizon.flag.application.port.in.FlagCommentCommandUseCase;
import com.example.DunbarHorizon.flag.domain.comment.FlagComment;
import com.example.DunbarHorizon.flag.domain.comment.exception.FlagCommentNotFoundException;
import com.example.DunbarHorizon.flag.domain.comment.repository.FlagCommentRepository;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlagCommentCommandService implements FlagCommentCommandUseCase {

    private final FlagCommentRepository commentRepository;
    private final FlagRepository flagRepository;

    @Override
    @Transactional
    public Long createRootComment(Long flagId, Long userId, String content, boolean isPrivate) {
        flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        FlagComment comment = FlagComment.createRoot(flagId, userId, content, isPrivate);
        return commentRepository.save(comment).getId();
    }

    @Override
    @Transactional
    public Long createReply(Long flagId, Long parentId, Long userId, String content, boolean isPrivate) {
        FlagComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new FlagCommentNotFoundException(parentId));
        validateBelongsToFlag(parent, flagId);

        flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        FlagComment reply = parent.createReply(userId, content, isPrivate);
        return commentRepository.save(reply).getId();
    }

    @Override
    @Transactional
    public void updateComment(Long flagId, Long commentId, Long userId, String content, boolean isPrivate) {
        FlagComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new FlagCommentNotFoundException(commentId));
        validateBelongsToFlag(comment, flagId);

        comment.update(userId, content, isPrivate);
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long flagId, Long commentId, Long userId) {
        FlagComment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new FlagCommentNotFoundException(commentId));
        validateBelongsToFlag(comment, flagId);

        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        comment.validateDeletionAuthority(userId, flag.getHostId());

        commentRepository.deleteWithReplies(comment.getId());
    }

    private void validateBelongsToFlag(FlagComment comment, Long flagId) {
        if (!flagId.equals(comment.getFlagId())) {
            throw new FlagCommentNotFoundException(comment.getId());
        }
    }
}
