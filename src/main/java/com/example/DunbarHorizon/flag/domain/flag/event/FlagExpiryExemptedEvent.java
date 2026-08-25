package com.example.DunbarHorizon.flag.domain.flag.event;

public record FlagExpiryExemptedEvent(
        Long flagId,
        Long hostId,
        Long parentId
) {}
