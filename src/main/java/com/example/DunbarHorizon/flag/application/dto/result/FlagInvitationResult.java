package com.example.DunbarHorizon.flag.application.dto.result;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;

import java.time.LocalDateTime;

public record FlagInvitationResult(
        Long id,
        Long flagId,
        String flagTitle,
        String flagDescription,
        String counterpartNickname,
        LocalDateTime createdAt
) {
    public static FlagInvitationResult of(
            FlagInvitation invitation,
            Flag flag,
            FlagUserInfo counterpart
    ) {
        return new FlagInvitationResult(
                invitation.getId(),
                invitation.getFlagId(),
                flag.getTitle(),
                flag.getDescription(),
                counterpart.nickname(),
                invitation.getCreatedAt()
        );
    }
}
