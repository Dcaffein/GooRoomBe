package com.example.DunbarHorizon.flag.adapter.in.web.dto;

import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitationStatus;
import jakarta.validation.constraints.NotNull;

public record FlagInvitationStatusUpdateRequest(
        @NotNull(message = "상태(status)는 필수입니다.")
        FlagInvitationStatus status
) {}
