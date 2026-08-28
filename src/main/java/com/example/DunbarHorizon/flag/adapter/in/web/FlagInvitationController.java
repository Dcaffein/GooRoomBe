package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.adapter.in.web.dto.FlagInviteRequest;
import com.example.DunbarHorizon.flag.adapter.in.web.dto.FlagInvitationStatusUpdateRequest;
import com.example.DunbarHorizon.flag.application.dto.FlagInvitationDirection;
import com.example.DunbarHorizon.flag.application.dto.result.FlagInvitationResult;
import com.example.DunbarHorizon.flag.application.port.in.FlagInvitationQueryUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagInvitationUseCase;
import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flag-invitations")
@RequiredArgsConstructor
public class FlagInvitationController {

    private final FlagInvitationUseCase flagInvitationUseCase;
    private final FlagInvitationQueryUseCase flagInvitationQueryUseCase;

    @PostMapping
    public ResponseEntity<Long> invite(
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagInviteRequest request
    ) {
        Long invitationId = flagInvitationUseCase.invite(request.flagId(), currentUserId, request.inviteeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationId);
    }

    @GetMapping
    public ResponseEntity<List<FlagInvitationResult>> getInvitations(
            @CurrentUserId Long currentUserId,
            @RequestParam String direction
    ) {
        return ResponseEntity.ok(flagInvitationQueryUseCase.getInvitations(
                currentUserId, FlagInvitationDirection.from(direction)
        ));
    }

    @PatchMapping("/{invitationId}")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long invitationId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagInvitationStatusUpdateRequest request
    ) {
        flagInvitationUseCase.updateStatus(invitationId, currentUserId, request.status());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long invitationId,
            @CurrentUserId Long currentUserId
    ) {
        flagInvitationUseCase.delete(invitationId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
