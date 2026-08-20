package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.adapter.in.web.dto.CommentCreateRequest;
import com.example.DunbarHorizon.flag.adapter.in.web.dto.CommentUpdateRequest;
import com.example.DunbarHorizon.flag.application.port.in.FlagCommentCommandUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagCommentQueryUseCase;
import com.example.DunbarHorizon.flag.application.dto.result.CommentResult;
import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FlagCommentController {

    private final FlagCommentCommandUseCase flagCommentCommandUseCase;
    private final FlagCommentQueryUseCase commentQueryUseCase;

    @GetMapping("/api/v1/flags/{flagId}/comments")
    public ResponseEntity<List<CommentResult>> getComments(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        List<CommentResult> commentTree = commentQueryUseCase.getCommentTree(flagId, currentUserId);
        return ResponseEntity.ok(commentTree);
    }

    @GetMapping("/api/v1/flags/{flagId}/comments/count")
    public ResponseEntity<Long> getCommentCount(@PathVariable Long flagId) {
        return ResponseEntity.ok(commentQueryUseCase.getCommentCount(flagId));
    }

    @PostMapping("/api/v1/flags/{flagId}/comments")
    public ResponseEntity<Long> createRootComment(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        Long commentId = flagCommentCommandUseCase.createRootComment(
                flagId, currentUserId, request.content(), request.isPrivate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(commentId);
    }

    @PostMapping("/api/v1/comments/{parentId}/replies")
    public ResponseEntity<Long> createReply(
            @PathVariable Long parentId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        Long replyId = flagCommentCommandUseCase.createReply(
                parentId, currentUserId, request.content(), request.isPrivate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(replyId);
    }

    @PatchMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long commentId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        flagCommentCommandUseCase.updateComment(
                commentId, currentUserId, request.content(), request.isPrivate()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            @CurrentUserId Long currentUserId
    ) {
        flagCommentCommandUseCase.deleteComment(commentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}