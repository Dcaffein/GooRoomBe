package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelCreateRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelMemberAddRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelMembersReplaceRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.LabelUpdateRequest;
import com.example.DunbarHorizon.social.application.dto.result.LabelMemberResult;
import com.example.DunbarHorizon.social.application.dto.result.LabelResult;
import com.example.DunbarHorizon.social.domain.label.Label;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockCustomUser
class LabelControllerTest extends BaseControllerTest {

    private final String labelId = "label-uuid-123";

    @Test
    @DisplayName("새로운 라벨을 성공적으로 생성한다")
    void createLabel_Success() throws Exception {
        LabelCreateRequest dto = new LabelCreateRequest("친한친구");

        Label mockLabel = mock(Label.class);
        SocialUser mockOwner = mock(SocialUser.class);

        given(mockLabel.getId()).willReturn(labelId);
        given(mockLabel.getLabelName()).willReturn("친한친구");
        given(mockLabel.getOwner()).willReturn(mockOwner);
        given(mockOwner.getId()).willReturn(1L);

        given(labelCommandUseCase.createLabel(eq(1L), eq("친한친구")))
                .willReturn(mockLabel);

        mockMvc.perform(post("/api/v1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/labels/" + labelId))
                .andExpect(jsonPath("$.id").value(labelId))
                .andExpect(jsonPath("$.labelName").value("친한친구"));
    }

    @Test
    @DisplayName("라벨을 삭제한다")
    void deleteLabel_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/labels/{labelId}", labelId))
                .andExpect(status().isNoContent());

        verify(labelCommandUseCase).deleteLabel(eq(1L), eq(labelId));
    }

    @Test
    @DisplayName("라벨의 이름을 업데이트한다")
    void updateLabel_Success() throws Exception {
        String newLabelName = "새로운 라벨명";
        LabelUpdateRequest dto = new LabelUpdateRequest(newLabelName);

        mockMvc.perform(patch("/api/v1/labels/{labelId}", labelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        verify(labelCommandUseCase).updateLabel(eq(labelId), eq(1L), eq(newLabelName));
    }

    @Test
    @DisplayName("라벨의 멤버를 일괄 교체한다")
    void replaceMembers_Success() throws Exception {
        LabelMembersReplaceRequest dto = new LabelMembersReplaceRequest(List.of(2L, 3L));

        mockMvc.perform(put("/api/v1/labels/{labelId}/members", labelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        verify(labelCommandUseCase).replaceLabelMembers(anyLong(), eq(labelId), eq(List.of(2L, 3L)));
    }

    @Test
    @DisplayName("라벨에 특정 멤버 하나를 추가한다")
    void addMember_Success() throws Exception {
        LabelMemberAddRequest dto = new LabelMemberAddRequest(2L);

        mockMvc.perform(post("/api/v1/labels/{labelId}/members", labelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/labels/" + labelId + "/members/2"));

        verify(labelCommandUseCase).addMemberToLabel(anyLong(), eq(labelId), eq(2L));
    }

    @Test
    @DisplayName("라벨에서 특정 멤버를 제거한다")
    void removeMember_Success() throws Exception {
        Long memberId = 2L;

        mockMvc.perform(delete("/api/v1/labels/{labelId}/members/{memberId}", labelId, memberId))
                .andExpect(status().isNoContent());

        verify(labelCommandUseCase).removeMemberFromLabel(anyLong(), eq(labelId), eq(memberId));
    }

    @Test
    @DisplayName("라벨 단건 정보를 조회한다")
    void getLabelById_Success() throws Exception {
        LabelResult result = new LabelResult(labelId, "친구들", 1);
        given(labelQueryUseCase.getLabelById(eq(1L), eq(labelId))).willReturn(result);

        mockMvc.perform(get("/api/v1/labels/{labelId}", labelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(labelId))
                .andExpect(jsonPath("$.labelName").value("친구들"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    @Test
    @DisplayName("특정 멤버가 속한 내 라벨 목록을 조회한다")
    void getAllLabels_ByMember() throws Exception {
        Long memberId = 2L;
        given(labelQueryUseCase.getLabelsByMember(eq(1L), eq(memberId)))
                .willReturn(List.of(new LabelResult(labelId, "친구들", 1)));

        mockMvc.perform(get("/api/v1/labels").param("memberId", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labelId))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].members").doesNotExist());

        verify(labelQueryUseCase).getLabelsByMember(1L, memberId);
    }

    @Test
    @DisplayName("라벨의 멤버 목록을 조회한다")
    void getLabelMembers_Success() throws Exception {
        List<LabelMemberResult> members = List.of(new LabelMemberResult(2L, "멤버닉네임"));
        given(labelQueryUseCase.getLabelMembers(eq(1L), eq(labelId))).willReturn(members);

        mockMvc.perform(get("/api/v1/labels/{labelId}/members", labelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nickname").value("멤버닉네임"));
    }
}
