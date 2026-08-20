package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.adapter.in.web.dto.FlagInviteRequest;
import com.example.DunbarHorizon.flag.application.dto.result.ReceivedFlagInvitationResult;
import com.example.DunbarHorizon.flag.application.dto.result.SentFlagInvitationResult;
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

    @GetMapping("/received")
    public ResponseEntity<List<ReceivedFlagInvitationResult>> getReceived(
            @CurrentUserId Long currentUserId
    ) {
        return ResponseEntity.ok(flagInvitationQueryUseCase.getReceived(currentUserId));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<SentFlagInvitationResult>> getSent(
            @CurrentUserId Long currentUserId
    ) {
        return ResponseEntity.ok(flagInvitationQueryUseCase.getSent(currentUserId));
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Void> accept(
            @PathVariable Long invitationId,
            @CurrentUserId Long currentUserId
    ) {
        flagInvitationUseCase.accept(invitationId, currentUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long invitationId,
            @CurrentUserId Long currentUserId
    ) {
        flagInvitationUseCase.reject(invitationId, currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long invitationId,
            @CurrentUserId Long currentUserId
    ) {
        flagInvitationUseCase.cancel(invitationId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
