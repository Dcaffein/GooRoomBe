package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelCreateRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelMemberAddRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelMembersReplaceRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelUpdateRequest;
import com.example.DunbarHorizon.social.application.port.in.LabelCommandUseCase;
import com.example.DunbarHorizon.social.application.port.in.LabelQueryUseCase;
import com.example.DunbarHorizon.social.application.dto.result.LabelMemberResult;
import com.example.DunbarHorizon.social.application.dto.result.LabelResult;
import com.example.DunbarHorizon.social.domain.label.Label;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelCommandUseCase labelCommandUseCase;
    private final LabelQueryUseCase labelQueryUseCase;

    @GetMapping
    public ResponseEntity<List<LabelResult>> getAllLabels(
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) Long memberId) {

        if (memberId != null) {
            return ResponseEntity.ok(labelQueryUseCase.getLabelsByMember(currentUserId, memberId));
        }
        return ResponseEntity.ok(labelQueryUseCase.getAllLabels(currentUserId));
    }

    @GetMapping("/{labelId}")
    public ResponseEntity<LabelResult> getLabelById(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId) {

        return ResponseEntity.ok(labelQueryUseCase.getLabelById(currentUserId, labelId));
    }

    @GetMapping("/{labelId}/members")
    public ResponseEntity<List<LabelMemberResult>> getLabelMembers(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId) {

        return ResponseEntity.ok(labelQueryUseCase.getLabelMembers(currentUserId, labelId));
    }

    @PostMapping
    public ResponseEntity<LabelResult> createLabel(
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid LabelCreateRequest dto) {

        Label newLabel = labelCommandUseCase.createLabel(currentUserId, dto.labelName());

        return ResponseEntity
                .created(URI.create("/api/v1/labels/" + newLabel.getId()))
                .body(LabelResult.from(newLabel));
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> deleteLabel(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId) {

        labelCommandUseCase.deleteLabel(currentUserId, labelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{labelId}")
    public ResponseEntity<Void> updateLabel(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId,
            @RequestBody @Valid LabelUpdateRequest dto) {

        labelCommandUseCase.updateLabel(labelId, currentUserId, dto.labelName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{labelId}/members")
    public ResponseEntity<Void> replaceMembers(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId,
            @RequestBody @Valid LabelMembersReplaceRequest labelMembersReplaceRequest) {

        labelCommandUseCase.replaceLabelMembers(currentUserId, labelId, labelMembersReplaceRequest.memberIds());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{labelId}/members")
    public ResponseEntity<Void> addMember(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId,
            @RequestBody @Valid LabelMemberAddRequest labelMemberAddRequest) {

        labelCommandUseCase.addMemberToLabel(currentUserId, labelId, labelMemberAddRequest.memberId());
        return ResponseEntity
                .created(URI.create("/api/v1/labels/" + labelId + "/members/" + labelMemberAddRequest.memberId()))
                .build();
    }

    @DeleteMapping("/{labelId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId,
            @PathVariable Long memberId) {

        labelCommandUseCase.removeMemberFromLabel(currentUserId, labelId, memberId);
        return ResponseEntity.noContent().build();
    }
}
