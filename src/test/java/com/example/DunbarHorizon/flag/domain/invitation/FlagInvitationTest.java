package com.example.DunbarHorizon.flag.domain.invitation;

import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationAccessException;
import com.example.DunbarHorizon.flag.domain.invitation.exception.FlagInvitationInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagInvitationTest {

    private static final Long FLAG_ID = 1L;
    private static final Long INVITER_ID = 2L;
    private static final Long INVITEE_ID = 3L;

    @Test
    @DisplayName("초대 상태는 ACCEPTED로만 변경할 수 있다")
    void updateStatus_UnsupportedStatus_Throws() {
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);

        assertThatThrownBy(() -> invitation.updateStatus(INVITEE_ID, null))
                .isInstanceOf(FlagInvitationInvalidException.class);
    }

    @Test
    @DisplayName("피초대자의 삭제 요청은 거절로 처리한다")
    void delete_ByInvitee_Succeeds() {
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);

        assertThatNoException().isThrownBy(() -> invitation.delete(INVITEE_ID));
    }

    @Test
    @DisplayName("초대자의 삭제 요청은 취소로 처리한다")
    void delete_ByInviter_Succeeds() {
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);

        assertThatNoException().isThrownBy(() -> invitation.delete(INVITER_ID));
    }

    @Test
    @DisplayName("초대와 무관한 사용자는 삭제할 수 없다")
    void delete_ByThirdParty_Throws() {
        FlagInvitation invitation = FlagInvitation.create(FLAG_ID, INVITER_ID, INVITEE_ID);

        assertThatThrownBy(() -> invitation.delete(99L))
                .isInstanceOf(FlagInvitationAccessException.class);
    }

}
