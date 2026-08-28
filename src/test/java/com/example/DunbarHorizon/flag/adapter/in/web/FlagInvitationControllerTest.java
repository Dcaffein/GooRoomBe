package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.application.dto.FlagInvitationDirection;
import com.example.DunbarHorizon.flag.application.dto.result.FlagInvitationResult;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitationStatus;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockCustomUser
class FlagInvitationControllerTest extends BaseControllerTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long FLAG_ID = 10L;
    private static final Long INVITEE_ID = 2L;
    private static final Long INVITATION_ID = 100L;

    @Test
    @DisplayName("초대 생성 시 201과 초대 ID를 반환하고 invite()를 호출한다")
    void invite_Returns201() throws Exception {
        given(flagInvitationUseCase.invite(FLAG_ID, CURRENT_USER_ID, INVITEE_ID)).willReturn(INVITATION_ID);
        String body = """
                {"flagId": 10, "inviteeId": 2}
                """;

        mockMvc.perform(post("/api/v1/flag-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(INVITATION_ID));

        verify(flagInvitationUseCase).invite(FLAG_ID, CURRENT_USER_ID, INVITEE_ID);
    }

    @Test
    @DisplayName("초대 생성 시 flagId가 없으면 400을 반환한다")
    void invite_MissingFlagId_Returns400() throws Exception {
        String body = """
                {"inviteeId": 2}
                """;

        mockMvc.perform(post("/api/v1/flag-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("초대 생성 시 inviteeId가 없으면 400을 반환한다")
    void invite_MissingInviteeId_Returns400() throws Exception {
        String body = """
                {"flagId": 10}
                """;

        mockMvc.perform(post("/api/v1/flag-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("받은 초대를 direction=received로 조회한다")
    void getInvitations_Received_Returns200() throws Exception {
        FlagInvitationResult result = new FlagInvitationResult(
                INVITATION_ID, FLAG_ID, "테스트 플래그", "설명", "상대방", LocalDateTime.now()
        );
        given(flagInvitationQueryUseCase.getInvitations(CURRENT_USER_ID, FlagInvitationDirection.RECEIVED))
                .willReturn(List.of(result));

        mockMvc.perform(get("/api/v1/flag-invitations").param("direction", "received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID))
                .andExpect(jsonPath("$[0].flagId").value(FLAG_ID))
                .andExpect(jsonPath("$[0].counterpartNickname").value("상대방"));

        verify(flagInvitationQueryUseCase)
                .getInvitations(CURRENT_USER_ID, FlagInvitationDirection.RECEIVED);
    }

    @Test
    @DisplayName("보낸 초대를 direction=sent로 조회한다")
    void getInvitations_Sent_Returns200() throws Exception {
        FlagInvitationResult result = new FlagInvitationResult(
                INVITATION_ID, FLAG_ID, "테스트 플래그", "설명", "상대방", LocalDateTime.now()
        );
        given(flagInvitationQueryUseCase.getInvitations(CURRENT_USER_ID, FlagInvitationDirection.SENT))
                .willReturn(List.of(result));

        mockMvc.perform(get("/api/v1/flag-invitations").param("direction", "sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID));

        verify(flagInvitationQueryUseCase)
                .getInvitations(CURRENT_USER_ID, FlagInvitationDirection.SENT);
    }

    @Test
    @DisplayName("초대 목록 조회에 direction이 없으면 400을 반환한다")
    void getInvitations_MissingDirection_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flag-invitations"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 direction이면 400을 반환한다")
    void getInvitations_InvalidDirection_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flag-invitations").param("direction", "all"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("초대 상태를 ACCEPTED로 변경하면 204를 반환한다")
    void updateStatus_Accepted_Returns204() throws Exception {
        String body = """
                {"status": "ACCEPTED"}
                """;

        mockMvc.perform(patch("/api/v1/flag-invitations/{invitationId}", INVITATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(flagInvitationUseCase)
                .updateStatus(INVITATION_ID, CURRENT_USER_ID, FlagInvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("초대 상태가 누락되면 400을 반환한다")
    void updateStatus_MissingStatus_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/flag-invitations/{invitationId}", INVITATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 초대 상태면 400을 반환한다")
    void updateStatus_UnsupportedStatus_Returns400() throws Exception {
        String body = """
                {"status": "REJECTED"}
                """;

        mockMvc.perform(patch("/api/v1/flag-invitations/{invitationId}", INVITATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("초대를 삭제하면 204를 반환한다")
    void delete_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/flag-invitations/{invitationId}", INVITATION_ID))
                .andExpect(status().isNoContent());

        verify(flagInvitationUseCase).delete(INVITATION_ID, CURRENT_USER_ID);
    }
}
