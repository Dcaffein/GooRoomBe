package com.example.DunbarHorizon.flag.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record FlagInviteRequest(
        @NotNull Long flagId,
        @NotNull Long inviteeId
) {}
