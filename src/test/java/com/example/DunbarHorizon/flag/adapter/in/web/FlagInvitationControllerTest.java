package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.application.dto.result.ReceivedFlagInvitationResult;
import com.example.DunbarHorizon.flag.application.dto.result.SentFlagInvitationResult;
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
    @DisplayName("플래그 하위의 구 초대 생성 URL은 더 이상 매핑되지 않는다")
    void invite_LegacyNestedUrl_Returns404() throws Exception {
        String body = """
                {"inviteeId": 2}
                """;

        mockMvc.perform(post("/api/v1/flags/{flagId}/invitations", FLAG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("받은 초대 목록 조회 시 200을 반환하고 getReceived()를 호출한다")
    void getReceived_Returns200() throws Exception {
        ReceivedFlagInvitationResult result = new ReceivedFlagInvitationResult(
                INVITATION_ID, FLAG_ID, "테스트 플래그", "설명", "초대한사람", LocalDateTime.now()
        );
        given(flagInvitationQueryUseCase.getReceived(CURRENT_USER_ID)).willReturn(List.of(result));

        mockMvc.perform(get("/api/v1/flag-invitations/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID))
                .andExpect(jsonPath("$[0].flagId").value(FLAG_ID));

        verify(flagInvitationQueryUseCase).getReceived(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("보낸 초대 목록 조회 시 200을 반환하고 getSent()를 호출한다")
    void getSent_Returns200() throws Exception {
        SentFlagInvitationResult result = new SentFlagInvitationResult(
                INVITATION_ID, FLAG_ID, "테스트 플래그", "설명", "초대받은사람", LocalDateTime.now()
        );
        given(flagInvitationQueryUseCase.getSent(CURRENT_USER_ID)).willReturn(List.of(result));

        mockMvc.perform(get("/api/v1/flag-invitations/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID));

        verify(flagInvitationQueryUseCase).getSent(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("초대 수락 시 200을 반환하고 accept()를 호출한다")
    void accept_Returns200() throws Exception {
        mockMvc.perform(post("/api/v1/flag-invitations/{invitationId}/accept", INVITATION_ID))
                .andExpect(status().isOk());

        verify(flagInvitationUseCase).accept(INVITATION_ID, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("초대 거절 시 200을 반환하고 reject()를 호출한다")
    void reject_Returns200() throws Exception {
        mockMvc.perform(post("/api/v1/flag-invitations/{invitationId}/reject", INVITATION_ID))
                .andExpect(status().isOk());

        verify(flagInvitationUseCase).reject(INVITATION_ID, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("초대 취소 시 204를 반환하고 cancel()를 호출한다")
    void cancel_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/flag-invitations/{invitationId}", INVITATION_ID))
                .andExpect(status().isNoContent());

        verify(flagInvitationUseCase).cancel(INVITATION_ID, CURRENT_USER_ID);
    }
}
