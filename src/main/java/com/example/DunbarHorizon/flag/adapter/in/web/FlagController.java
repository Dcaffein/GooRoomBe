package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.adapter.in.web.dto.*;
import com.example.DunbarHorizon.flag.application.dto.result.FlagDetailResult;
import com.example.DunbarHorizon.flag.application.dto.result.FlagResult;
import com.example.DunbarHorizon.flag.application.port.in.*;
import com.example.DunbarHorizon.flag.application.port.in.command.*;
import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.ServletRequestBindingException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flags")
@RequiredArgsConstructor
public class FlagController {

    private final FlagHostUseCase flagHostUseCase;
    private final FlagModificationUseCase flagModificationUseCase;
    private final FlagParticipationUseCase flagParticipationUseCase;
    private final FlagInvitationUseCase flagInvitationUseCase;
    private final FlagQueryUseCase flagQueryUseCase;

    @PostMapping
    public ResponseEntity<Long> createFlag(
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagCreateRequest request
    ) {
        if (request.parentFlagId() != null) {
            FlagEncoreCommand command = new FlagEncoreCommand(
                    request.parentFlagId(), currentUserId,
                    request.deadline(), request.startDateTime(), request.endDateTime()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(flagHostUseCase.encoreFlag(command));
        }

        FlagHostCommand command = new FlagHostCommand(
                currentUserId, request.title(), request.description(),
                request.capacity(), request.deadline(),
                request.startDateTime(), request.endDateTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(flagHostUseCase.hostFlag(command));
    }

    @PatchMapping("/{flagId}/details")
    public ResponseEntity<Void> modifyDetails(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagDetailsUpdateRequest request
    ) {
        flagModificationUseCase.modifyFlagDetails(new FlagDetailsUpdateCommand(
                flagId, currentUserId, request.title(), request.description()
        ));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{flagId}/capacity")
    public ResponseEntity<Void> modifyCapacity(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagCapacityUpdateRequest request
    ) {
        flagModificationUseCase.modifyFlagCapacity(new FlagCapacityUpdateCommand(
                flagId, currentUserId, request.capacity()
        ));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{flagId}/schedule")
    public ResponseEntity<Void> replaceSchedule(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagScheduleUpdateRequest request
    ) {
        flagModificationUseCase.reschedule(new FlagScheduleUpdateCommand(
                flagId, currentUserId, request.deadline(), request.startDateTime(), request.endDateTime()
        ));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{flagId}/schedule/deadline")
    public ResponseEntity<Void> closeRecruitment(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        flagModificationUseCase.closeRecruitment(flagId, currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{flagId}")
    public ResponseEntity<Void> deleteFlag(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        flagModificationUseCase.closeFlag(flagId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{flagId}/participants")
    public ResponseEntity<Void> participate(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        flagParticipationUseCase.participateInFlag(flagId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{flagId}/participants/me")
    public ResponseEntity<Void> leave(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        flagParticipationUseCase.leaveFlag(flagId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{flagId}/participants/{participantId}")
    public ResponseEntity<Void> updateInvitePermission(
            @PathVariable Long flagId,
            @PathVariable Long participantId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FlagInvitePermissionRequest request
    ) {
        flagInvitationUseCase.updateInvitePermission(flagId, currentUserId, participantId, request.canInvite());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Slice<FlagResult>> getMyFlagsByRole(
            @CurrentUserId Long currentUserId,
            @RequestParam FlagRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) throws ServletRequestBindingException {
        return ResponseEntity.ok(flagQueryUseCase.getFlagsByRole(currentUserId, role, pageable(page, size)));
    }

    @GetMapping("/feed")
    public ResponseEntity<Slice<FlagResult>> getFeedFlags(
            @CurrentUserId Long currentUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) throws ServletRequestBindingException {
        return ResponseEntity.ok(flagQueryUseCase.getFeedFlags(currentUserId, pageable(page, size)));
    }

    @GetMapping("/profile")
    public ResponseEntity<List<FlagResult>> getProfileFlags(
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(flagQueryUseCase.getProfileFlags(userId));
    }

    @GetMapping("/{flagId}")
    public ResponseEntity<FlagDetailResult> getFlagDetail(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        return ResponseEntity.ok(flagQueryUseCase.getFlagDetail(flagId, currentUserId));
    }

    private Pageable pageable(int page, int size) throws ServletRequestBindingException {
        if (page < 0 || size < 1) {
            throw new ServletRequestBindingException("page는 0 이상, size는 1 이상이어야 합니다.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
