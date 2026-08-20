package com.example.DunbarHorizon.flag.domain.invitation;

import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationAccessException;
import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationExpiredException;
import com.example.DunbarHorizon.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "flag_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagInvitation extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flagId;
    private Long inviterId;
    private Long inviteeId;

    private LocalDateTime expiresAt;

    private FlagInvitation(Long flagId, Long inviterId, Long inviteeId, LocalDateTime expiresAt) {
        this.flagId = flagId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.expiresAt = expiresAt;
    }

    public static FlagInvitation create(Long flagId, Long inviterId, Long inviteeId, LocalDateTime expiresAt) {
        return new FlagInvitation(flagId, inviterId, inviteeId, expiresAt);
    }

    public void accept(Long requesterId) {
        validateInvitee(requesterId);
        validateNotExpired();
    }

    public void reject(Long requesterId) {
        validateInvitee(requesterId);
    }

    public void cancel(Long requesterId) {
        if (!inviterId.equals(requesterId)) {
            throw new FlagInvitationAccessException("초대를 보낸 본인만 취소할 수 있습니다.");
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    private void validateInvitee(Long requesterId) {
        if (!inviteeId.equals(requesterId)) {
            throw new FlagInvitationAccessException("초대받은 본인만 응답할 수 있습니다.");
        }
    }

    private void validateNotExpired() {
        if (isExpired()) {
            throw new FlagInvitationExpiredException();
        }
    }
}
