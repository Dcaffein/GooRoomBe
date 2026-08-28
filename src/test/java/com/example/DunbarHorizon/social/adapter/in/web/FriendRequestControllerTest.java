package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.social.adapter.in.web.dto.FriendRequestCreateRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.FriendRequestStatusUpdateRequest;
import com.example.DunbarHorizon.social.application.dto.FriendRequestDirection;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockCustomUser
class FriendRequestControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("친구 요청을 성공적으로 보낸다")
    void sendFriendRequest_Success() throws Exception {
        Long receiverId = 2L;
        FriendRequestCreateRequest dto = new FriendRequestCreateRequest(receiverId);

        FriendRequest mockRequest = mock(FriendRequest.class);
        SocialUser mockRequester = mock(SocialUser.class);
        SocialUser mockReceiver = mock(SocialUser.class);

        given(mockRequest.getId()).willReturn("newRequest");
        given(mockRequest.getRequester()).willReturn(mockRequester);
        given(mockRequest.getReceiver()).willReturn(mockReceiver);
        given(mockRequest.getStatus()).willReturn(FriendRequestStatus.PENDING);
        given(mockRequester.getId()).willReturn(1L);
        given(mockReceiver.getId()).willReturn(2L);

        given(friendRequesterActionUseCase.sendRequest(eq(1L), eq(receiverId)))
                .willReturn(mockRequest);

        mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/friend-requests/2"))
                .andExpect(jsonPath("$.id").value("newRequest"));
    }

    @Test
    @DisplayName("친구 요청을 수락한다")
    void acceptFriendRequest_Success() throws Exception {
        Long counterpartId = 2L;
        FriendRequestStatusUpdateRequest request =
                new FriendRequestStatusUpdateRequest(FriendRequestStatus.ACCEPTED);

        mockMvc.perform(patch("/api/v1/friend-requests/{counterpartId}", counterpartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(friendRequestReceiverActionUseCase)
                .updateStatus(eq(1L), eq(counterpartId), eq(FriendRequestStatus.ACCEPTED));
    }

    @Test
    @DisplayName("친구 요청을 숨긴다")
    void hideFriendRequest_Success() throws Exception {
        Long counterpartId = 2L;
        FriendRequestStatusUpdateRequest request =
                new FriendRequestStatusUpdateRequest(FriendRequestStatus.HIDDEN);

        mockMvc.perform(patch("/api/v1/friend-requests/{counterpartId}", counterpartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(friendRequestReceiverActionUseCase)
                .updateStatus(eq(1L), eq(counterpartId), eq(FriendRequestStatus.HIDDEN));
    }

    @Test
    @DisplayName("친구 요청 숨김을 취소한다")
    void undoHideFriendRequest_Success() throws Exception {
        Long counterpartId = 2L;
        FriendRequestStatusUpdateRequest request =
                new FriendRequestStatusUpdateRequest(FriendRequestStatus.PENDING);

        mockMvc.perform(patch("/api/v1/friend-requests/{counterpartId}", counterpartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(friendRequestReceiverActionUseCase)
                .updateStatus(eq(1L), eq(counterpartId), eq(FriendRequestStatus.PENDING));
    }

    @Test
    @DisplayName("친구 요청을 취소한다")
    void cancelFriendRequest_Success() throws Exception {
        Long counterpartId = 2L;

        mockMvc.perform(delete("/api/v1/friend-requests/{counterpartId}", counterpartId))
                .andExpect(status().isNoContent());

        verify(friendRequesterActionUseCase).cancelRequest(eq(counterpartId), eq(1L));
    }

    @Test
    @DisplayName("상태 없이 친구 요청을 변경하면 400을 반환한다")
    void updateFriendRequestStatus_WithoutStatus_BadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/friend-requests/{counterpartId}", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("숨김 처리된 친구 요청 목록을 조회한다")
    void getHiddenRequests_Success() throws Exception {
        given(friendRequestQueryUseCase.getRequests(eq(1L), eq(FriendRequestDirection.RECEIVED),
                eq(FriendRequestStatus.HIDDEN))).willReturn(List.of());

        mockMvc.perform(get("/api/v1/friend-requests")
                        .param("direction", "received")
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("received 조회에서 status를 생략하면 PENDING으로 조회한다")
    void getReceivedRequests_DefaultStatus() throws Exception {
        given(friendRequestQueryUseCase.getRequests(eq(1L), eq(FriendRequestDirection.RECEIVED), isNull()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/friend-requests")
                        .param("direction", "received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("내가 보낸 친구 요청 목록을 조회한다")
    void getSentRequests_Success() throws Exception {
        given(friendRequestQueryUseCase.getRequests(eq(1L), eq(FriendRequestDirection.SENT), isNull()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/friend-requests")
                        .param("direction", "sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("조회 direction이 없으면 400을 반환한다")
    void getRequests_WithoutDirection_BadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/friend-requests"))
                .andExpect(status().isBadRequest());
    }
}
