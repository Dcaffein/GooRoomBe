package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.social.application.dto.result.SocialProfileResult;
import com.example.DunbarHorizon.social.domain.socialUser.exception.UserReferenceNotFoundException;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockCustomUser
class SocialUserControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("존재하는 유저 ID로 소셜 프로필을 조회하면 200과 경량 프로필을 반환한다")
    void getSocialProfile_Success() throws Exception {
        // given
        SocialProfileResult profile = new SocialProfileResult(2L, "친구", "https://img.com/friend.png");
        given(socialUserQueryUseCase.getSocialProfile(2L)).willReturn(profile);

        // when & then
        mockMvc.perform(get("/api/v1/social/profiles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.nickname").value("친구"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.com/friend.png"));
    }

    @Test
    @DisplayName("존재하지 않는 유저 ID로 소셜 프로필을 조회하면 404를 반환한다")
    void getSocialProfile_UserNotFound_Returns404() throws Exception {
        // given
        given(socialUserQueryUseCase.getSocialProfile(999L))
                .willThrow(new UserReferenceNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/v1/social/profiles/999"))
                .andExpect(status().isNotFound());
    }
}
