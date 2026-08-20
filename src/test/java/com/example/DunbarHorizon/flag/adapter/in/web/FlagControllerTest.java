package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.FlagDetailResult;
import com.example.DunbarHorizon.flag.application.port.in.FlagRole;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockCustomUser
class FlagControllerTest extends BaseControllerTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Test
    @DisplayName("일반 플래그 생성 시 201을 반환하고 hostFlag()를 호출한다")
    void createFlag_Normal_Returns201() throws Exception {
        given(flagHostUseCase.hostFlag(any())).willReturn(1L);
        String body = """
                {
                  "title": "테스트 플래그",
                  "description": "플래그 설명",
                  "capacity": 10,
                  "startDateTime": "2030-12-01T10:00:00",
                  "endDateTime": "2030-12-01T12:00:00"
                }
                """;

        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(flagHostUseCase).hostFlag(any());
    }

    @Test
    @DisplayName("parentFlagId가 있으면 encoreFlag()를 호출한다")
    void createFlag_Encore_CallsEncoreFlag() throws Exception {
        given(flagHostUseCase.encoreFlag(any())).willReturn(2L);
        String body = """
                {
                  "parentFlagId": 1,
                  "title": "앵코르 플래그",
                  "description": "설명",
                  "capacity": 10,
                  "startDateTime": "2030-12-01T10:00:00",
                  "endDateTime": "2030-12-01T12:00:00"
                }
                """;

        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(flagHostUseCase).encoreFlag(any());
    }

    @Test
    @DisplayName("플래그 상세정보 수정 시 200을 반환하고 modifyFlagDetails()를 호출한다")
    void modifyDetails_Returns200() throws Exception {
        String body = """
                {"title": "새 제목", "description": "새 설명"}
                """;

        mockMvc.perform(patch("/api/v1/flags/1/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(flagManagementUseCase).modifyFlagDetails(any());
    }

    @Test
    @DisplayName("플래그 정원 수정 시 200을 반환하고 modifyFlagCapacity()를 호출한다")
    void modifyCapacity_Returns200() throws Exception {
        String body = """
                {"capacity": 20}
                """;

        mockMvc.perform(patch("/api/v1/flags/1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(flagManagementUseCase).modifyFlagCapacity(any());
    }

    @Test
    @DisplayName("플래그 일정 변경 시 200을 반환하고 reschedule()를 호출한다")
    void replaceSchedule_Returns200() throws Exception {
        String body = """
                {
                  "startDateTime": "2030-12-02T10:00:00",
                  "endDateTime": "2030-12-02T12:00:00"
                }
                """;

        mockMvc.perform(put("/api/v1/flags/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(flagManagementUseCase).reschedule(any());
    }

    @Test
    @DisplayName("모집 종료 요청 시 200을 반환하고 closeRecruitment()를 호출한다")
    void closeRecruitment_Returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/flags/1/schedule/deadline"))
                .andExpect(status().isOk());

        verify(flagManagementUseCase).closeRecruitment(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("플래그 삭제 시 204를 반환하고 closeFlag()를 호출한다")
    void deleteFlag_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/1"))
                .andExpect(status().isNoContent());

        verify(flagManagementUseCase).closeFlag(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("플래그 참여 시 201을 반환하고 participateInFlag()를 호출한다")
    void participate_Returns201() throws Exception {
        mockMvc.perform(post("/api/v1/flags/1/participants"))
                .andExpect(status().isCreated());

        verify(flagParticipationUseCase).participateInFlag(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("플래그 탈퇴 시 204를 반환하고 leaveFlag()를 호출한다")
    void leave_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/1/participants/me"))
                .andExpect(status().isNoContent());

        verify(flagParticipationUseCase).leaveFlag(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("참여자 컬렉션 루트의 DELETE는 405를 반환한다")
    void leave_CollectionRoot_Returns405() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/1/participants"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("참여자 초대 권한 수정 시 200을 반환하고 updateInvitePermission()을 호출한다")
    void updateInvitePermission_Returns200() throws Exception {
        String body = """
                {"canInvite": true}
                """;

        mockMvc.perform(patch("/api/v1/flags/1/participants/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(flagInvitationUseCase).updateInvitePermission(1L, CURRENT_USER_ID, 2L, true);
    }

    @Test
    @DisplayName("참여자 수정 시 canInvite가 없으면 400을 반환한다")
    void updateInvitePermission_MissingCanInvite_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/flags/1/participants/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("구 invite-permission URL은 더 이상 매핑되지 않는다")
    void updateInvitePermission_LegacyUrl_Returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/flags/1/participants/2/invite-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canInvite": true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("플래그 상세 조회 시 200과 함께 isHost 포함 응답을 반환하고 currentUserId를 전달한다")
    void getFlagDetail_Returns200WithIsHost() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now();
        FlagSchedule schedule = FlagSchedule.of(now.plusHours(1), now.plusHours(2), now.plusHours(3));
        Flag flag = Flag.create(CURRENT_USER_ID, "테스트 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", 1L);
        FlagDetailResult detail = FlagDetailResult.of(
                flag, new FlagUserInfo(CURRENT_USER_ID, "호스트", null), null, List.of(), true
        );
        given(flagQueryUseCase.getFlagDetail(1L, CURRENT_USER_ID)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/flags/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.isHost").value(true));

        verify(flagQueryUseCase).getFlagDetail(1L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 플래그 상세 조회 시 404를 반환한다")
    void getFlagDetail_NotFound_Returns404() throws Exception {
        // given
        given(flagQueryUseCase.getFlagDetail(999L, CURRENT_USER_ID))
                .willThrow(new FlagNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/v1/flags/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("친구 플래그 조회 시 200을 반환하고 getFriendFlags()를 호출한다")
    void getFriendFlags_Returns200() throws Exception {
        given(flagQueryUseCase.getFriendFlags(CURRENT_USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/flags/friends"))
                .andExpect(status().isOk());

        verify(flagQueryUseCase).getFriendFlags(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("내 플래그 조회(HOST) 시 200을 반환하고 getFlagsByRole()를 호출한다")
    void getMyFlags_HostRole_Returns200() throws Exception {
        given(flagQueryUseCase.getFlagsByRole(CURRENT_USER_ID, FlagRole.HOST)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/flags/me").param("role", "HOST"))
                .andExpect(status().isOk());

        verify(flagQueryUseCase).getFlagsByRole(CURRENT_USER_ID, FlagRole.HOST);
    }

    @Test
    @DisplayName("특정 유저 최근 플래그 조회 시 200을 반환하고 getRecentFlags()를 호출한다")
    void getRecentFlags_Returns200() throws Exception {
        Long targetUserId = 2L;
        given(flagQueryUseCase.getRecentFlags(targetUserId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/flags/users/{userId}/recent", targetUserId))
                .andExpect(status().isOk());

        verify(flagQueryUseCase).getRecentFlags(targetUserId);
    }

    @Test
    @DisplayName("특정 유저 플래그 조회(PARTICIPANT) 시 200을 반환하고 getFlagsByRole()를 호출한다")
    void getUserFlags_ParticipantRole_Returns200() throws Exception {
        Long targetUserId = 2L;
        given(flagQueryUseCase.getFlagsByRole(targetUserId, FlagRole.PARTICIPANT)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/flags/users/{userId}", targetUserId).param("role", "PARTICIPANT"))
                .andExpect(status().isOk());

        verify(flagQueryUseCase).getFlagsByRole(targetUserId, FlagRole.PARTICIPANT);
    }
}
