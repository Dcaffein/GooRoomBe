package com.example.DunbarHorizon.flag.domain.invitation;

import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationAccessException;
import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationInvalidException;
import com.example.DunbarHorizon.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private FlagInvitation(Long flagId, Long inviterId, Long inviteeId) {
        this.flagId = flagId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
    }

    public static FlagInvitation create(Long flagId, Long inviterId, Long inviteeId) {
        return new FlagInvitation(flagId, inviterId, inviteeId);
    }

    public void updateStatus(Long requesterId, FlagInvitationStatus status) {
        if (status != FlagInvitationStatus.ACCEPTED) {
            throw new FlagInvitationInvalidException("초대 상태는 ACCEPTED로만 변경할 수 있습니다.");
        }
        validateInvitee(requesterId);
    }

    public void delete(Long requesterId) {
        if (inviteeId.equals(requesterId)) {
            reject(requesterId);
            return;
        }
        cancel(requesterId);
    }

    private void reject(Long requesterId) {
        validateInvitee(requesterId);
    }

    private void cancel(Long requesterId) {
        validateInviter(requesterId);
    }

    private void validateInvitee(Long requesterId) {
        if (!inviteeId.equals(requesterId)) {
            throw new FlagInvitationAccessException("초대받은 본인만 응답할 수 있습니다.");
        }
    }

    private void validateInviter(Long requesterId) {
        if (!inviterId.equals(requesterId)) {
            throw new FlagInvitationAccessException("초대를 보낸 본인만 취소할 수 있습니다.");
        }
    }

}
