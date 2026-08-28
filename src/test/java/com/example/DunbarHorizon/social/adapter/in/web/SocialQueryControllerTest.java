package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.social.application.dto.result.AnchorExpansionResult;
import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;
import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockCustomUser
class SocialQueryControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("메인 홈 네트워크를 기본 크기(DUNBAR)로 조회한다")
    void getFriendsNetwork_DefaultCircleSize() throws Exception {
        given(socialNetworkQueryUseCase.getFriendsNetwork(eq(1L), eq(DunbarCircle.DUNBAR)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/network"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("라벨 네트워크를 조회한다")
    void getLabelNetwork_Success() throws Exception {
        String labelId = "label-1";
        given(socialNetworkQueryUseCase.getLabelNetwork(eq(1L), eq(labelId)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/network/labels/{labelId}", labelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("앵커의 intimacy 기반으로 동적 파라미터를 적용한 추천 목록을 조회한다")
    void getAnchorRecommendation_Success() throws Exception {
        Long anchorId = 2L;
        given(socialExpansionQueryUseCase.getRecommendationsByAnchor(eq(1L), eq(anchorId))).willReturn(List.of());

        mockMvc.perform(get("/api/v1/network/recommendations")
                        .param("anchorId", String.valueOf(anchorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("연결 중개인 조회는 상위 3명과 전체 수를 내려주고 score는 포함하지 않는다")
    void getConnectionPath_Success() throws Exception {
        Long targetId = 99L;
        given(socialConnectionPathQueryUseCase.getConnectionPath(eq(1L), eq(targetId)))
                .willReturn(new ConnectionPathResult(false, 12, List.of(
                        new ConnectionPathResult.IntermediaryResult(2L, "중개인2"),
                        new ConnectionPathResult.IntermediaryResult(3L, "중개인3"),
                        new ConnectionPathResult.IntermediaryResult(4L, "중개인4")
                )));

        mockMvc.perform(get("/api/v1/network/path")
                        .param("targetId", String.valueOf(targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direct").value(false))
                .andExpect(jsonPath("$.totalCount").value(12))
                .andExpect(jsonPath("$.intermediaries.length()").value(3))
                .andExpect(jsonPath("$.intermediaries[0].userId").value(2))
                .andExpect(jsonPath("$.intermediaries[0].nickname").value("중개인2"))
                .andExpect(jsonPath("$.intermediaries[0].score").doesNotExist());
    }
    @Test
    @DisplayName("edges 경로로 직접 친구와 2-hop 엣지를 통합 조회한다")
    void getNetworkEdges_Success() throws Exception {
        Long targetId = 2L;
        given(socialNetworkQueryUseCase.getNetworkEdges(eq(1L), eq(targetId), eq(List.of(3L, 4L))))
                .willReturn(List.of(new MutualFriendEdgeResult(targetId, 3L, null)));

        mockMvc.perform(get("/api/v1/network/edges")
                        .param("targetId", String.valueOf(targetId))
                        .param("baseNetworkFriendIds", "3", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendAId").value(2))
                .andExpect(jsonPath("$[0].friendBId").value(3))
                .andExpect(jsonPath("$[0].intimacy").doesNotExist());
    }
}
