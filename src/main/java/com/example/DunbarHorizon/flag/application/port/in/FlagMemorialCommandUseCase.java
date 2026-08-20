package com.example.DunbarHorizon.flag.application.port.in;

public interface FlagMemorialCommandUseCase {
    Long createMemorial(Long flagId, Long userId, String content);
    void updateMemorial(Long flagId, Long memorialId, Long requesterId, String content);
    void deleteMemorial(Long flagId, Long memorialId, Long requesterId);
}
