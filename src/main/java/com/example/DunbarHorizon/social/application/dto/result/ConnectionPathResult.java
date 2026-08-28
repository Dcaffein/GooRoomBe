package com.example.DunbarHorizon.social.application.dto.result;

import java.util.List;

public record ConnectionPathResult(
        boolean direct,
        int totalCount,
        List<IntermediaryResult> intermediaries
) {
    public record IntermediaryResult(Long userId, String nickname) {}

    /** 리포지토리가 상한 적용 목록과 전체 수를 함께 반환하기 위한 타입 */
    public record Intermediaries(List<IntermediaryResult> items, int totalCount) {}
}
