package com.example.GooRoomBe.social.label.api;

import com.example.GooRoomBe.social.label.api.dto.*;
import com.example.GooRoomBe.social.label.application.LabelService;
import com.example.GooRoomBe.social.label.domain.Label;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    public ResponseEntity<LabelResponseDto> createLabel(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody @Valid LabelCreateRequestDto dto) {

        Label newLabel = labelService.createLabel(currentUserId, dto.labelName(), dto.exposure());

        LabelResponseDto response = LabelResponseDto.from(newLabel);

        return ResponseEntity
                .created(URI.create("/api/v1/labels/" + newLabel.getId()))
                .body(response);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> deleteLabel(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String labelId) {

        labelService.deleteLabel(currentUserId, labelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{labelId}")
    public ResponseEntity<Void> updateLabel(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String labelId,
            @RequestBody @Valid LabelUpdateRequestDto dto) {

        labelService.updateLabel(labelId, currentUserId, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{labelId}/members")
    public ResponseEntity<Void> replaceMembers(
            @PathVariable String labelId,
            @RequestBody @Valid LabelMembersReplaceRequestDto labelMembersReplaceRequestDto) {

        labelService.replaceMembers(labelId, labelMembersReplaceRequestDto.memberIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{labelId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable String labelId,
            @RequestBody @Valid LabelMemberAddRequestDto labelMemberAddRequestDto) {

        labelService.addMember(labelId, labelMemberAddRequestDto.memberId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{labelId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String labelId,
            @PathVariable String memberId) {

        labelService.removeMember(labelId, memberId);
        return ResponseEntity.noContent().build();
    }
}