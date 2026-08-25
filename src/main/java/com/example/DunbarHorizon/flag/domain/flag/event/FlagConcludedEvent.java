package com.example.DunbarHorizon.flag.domain.flag.event;

import java.util.List;

public record FlagConcludedEvent(
        Long flagId,
        Long hostId,
        Long parentId,
        List<Long> participantIds
) {
    public FlagConcludedEvent {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("참여자가 없는 플래그는 종료 사실을 발행하지 않습니다.");
        }
    }

    public boolean isEncore() {
        return parentId != null;
    }
}
