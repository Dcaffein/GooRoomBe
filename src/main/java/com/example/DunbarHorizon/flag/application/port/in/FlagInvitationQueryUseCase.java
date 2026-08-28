package com.example.DunbarHorizon.flag.application.port.in;

import com.example.DunbarHorizon.flag.application.dto.FlagInvitationDirection;
import com.example.DunbarHorizon.flag.application.dto.result.FlagInvitationResult;

import java.util.List;

public interface FlagInvitationQueryUseCase {
    List<FlagInvitationResult> getInvitations(Long userId, FlagInvitationDirection direction);
}
