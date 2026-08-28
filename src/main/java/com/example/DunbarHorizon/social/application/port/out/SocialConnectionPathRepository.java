package com.example.DunbarHorizon.social.application.port.out;

import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;

public interface SocialConnectionPathRepository {
    ConnectionPathResult.Intermediaries findIntermediaries(Long myId, Long targetId, int limit);
}
