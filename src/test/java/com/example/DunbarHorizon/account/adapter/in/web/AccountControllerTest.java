package com.example.DunbarHorizon.account.adapter.in.web;

import com.example.DunbarHorizon.account.adapter.in.web.dto.LoginRequestDto;
import com.example.DunbarHorizon.account.adapter.in.web.dto.SignupRequestDto;
import com.example.DunbarHorizon.account.adapter.in.web.dto.UserProfileUpdateRequest;
import com.example.DunbarHorizon.account.adapter.in.web.dto.VerificationEmailRequestDto;
import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.domain.exception.RefreshTokenNotFoundException;
import com.example.DunbarHorizon.account.domain.exception.TokenTheftDetectedException;
import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import com.example.DunbarHorizon.global.security.exception.InvalidTokenException;
import com.example.DunbarHorizon.support.BaseControllerTest;
import com.example.DunbarHorizon.support.WithMockCustomUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("회원가입 요청 시 201 Created를 반환한다")
    void signup_Success() throws Exception {
        SignupRequestDto request = new SignupRequestDto("test@test.com", "tester", "Pw123!@#");

        mockMvc.perform(post("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(signupUseCase).signup(eq("test@test.com"), eq("Pw123!@#"), eq("tester"));
    }

    @Test
    @DisplayName("로그인 성공 시 쿠키를 설정하고 201 Created를 반환한다")
    void login_Success() throws Exception {
        LoginRequestDto request = new LoginRequestDto("test@test.com", "password123");
        AuthTokenResult result = new AuthTokenResult("access-token", "refresh-token");

        given(loginUseCase.login(anyString(), anyString())).willReturn(result);

        mockMvc.perform(post("/api/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authCookieManager).addAccessTokenCookie(any(), eq("access-token"));
        verify(authCookieManager).addRefreshTokenCookie(any(), eq("refresh-token"));
    }

    @Test
    @DisplayName("로그아웃 시 쿠키를 만료시키고 204 No Content를 반환한다")
    void logout_Success() throws Exception {
        mockMvc.perform(delete("/api/auth/tokens")
                        .cookie(new Cookie("refresh_token", "some-rt")))
                .andExpect(status().isNoContent());

        verify(loginUseCase).logout(eq("some-rt"), isNull());
        verify(authCookieManager).addExpiredTokenCookie(any());
    }

    @Test
    @DisplayName("토큰 재발급 시 새로운 쿠키를 설정하고 200 OK를 반환한다")
    void reissue_Success() throws Exception {
        String oldRt = "old-rt";
        AuthTokenResult newResult = new AuthTokenResult("new-at", "new-rt");

        given(loginUseCase.reissue(oldRt)).willReturn(newResult);

        mockMvc.perform(patch("/api/auth/tokens")
                        .cookie(new Cookie("refresh_token", oldRt)))
                .andExpect(status().isOk());

        verify(authCookieManager).addAccessTokenCookie(any(), eq("new-at"));
        verify(authCookieManager).addRefreshTokenCookie(any(), eq("new-rt"));
    }

    @Test
    @DisplayName("재발급 시 refresh token이 만료되었으면 401과 ExpiredTokenException을 반환한다")
    void reissue_Expired_Returns401() throws Exception {
        // given - 전 사용자가 7일마다 겪는 정상 흐름이다. 과거에는 500이 나가
        //         프론트의 401 재로그인 분기를 비껴갔다.
        String oldRt = "expired-rt";
        given(loginUseCase.reissue(oldRt)).willThrow(new ExpiredTokenException());

        // when & then
        mockMvc.perform(patch("/api/auth/tokens")
                        .cookie(new Cookie("refresh_token", oldRt)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ExpiredTokenException"))
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
    }

    @Test
    @DisplayName("재발급 시 refresh token이 위조되었으면 401과 InvalidTokenException을 반환한다")
    void reissue_Invalid_Returns401() throws Exception {
        // given
        String forgedRt = "forged-rt";
        given(loginUseCase.reissue(forgedRt)).willThrow(new InvalidTokenException());

        // when & then
        mockMvc.perform(patch("/api/auth/tokens")
                        .cookie(new Cookie("refresh_token", forgedRt)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("InvalidTokenException"));
    }

    @Test
    @DisplayName("재발급 시 refresh_token 쿠키가 없으면 401과 RefreshTokenNotFoundException을 반환한다")
    void reissue_NoCookie_Returns401() throws Exception {
        // given - @CookieValue(required = false)이므로 null이 그대로 유스케이스에 전달된다
        given(loginUseCase.reissue(null)).willThrow(new RefreshTokenNotFoundException());

        // when & then
        mockMvc.perform(patch("/api/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("RefreshTokenNotFoundException"));
    }

    @Test
    @DisplayName("재발급 시 토큰 재사용이 탐지되면 403과 TokenTheftDetectedException을 반환한다")
    void reissue_TokenTheft_Returns403() throws Exception {
        // given - 만료·위조(401)와 달리 재사용 탐지는 방어 동작이므로 403을 유지한다
        String stolenRt = "stolen-rt";
        given(loginUseCase.reissue(stolenRt)).willThrow(new TokenTheftDetectedException());

        // when & then
        mockMvc.perform(patch("/api/auth/tokens")
                        .cookie(new Cookie("refresh_token", stolenRt)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("TokenTheftDetectedException"));
    }

    @Test
    @DisplayName("이메일 인증 메일 발송 요청 시 201 Created를 반환한다")
    void sendVerificationEmail_Success() throws Exception {
        VerificationEmailRequestDto request = new VerificationEmailRequestDto("test@test.com", "http://redirect.com");

        mockMvc.perform(post("/api/auth/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(verificationUseCase).sendVerificationEmail(eq("test@test.com"), eq("http://redirect.com"));
    }

    @Test
    @DisplayName("이메일 토큰 검증 성공 시 200 OK를 반환한다")
    void verifyEmail_Success() throws Exception {
        String token = "valid-token";

        mockMvc.perform(patch("/api/auth/verifications")
                        .param("token", token))
                .andExpect(status().isOk());

        verify(verificationUseCase).verifyEmail(eq(token));
    }

    @Test
    @WithMockCustomUser
    @DisplayName("profileImageKey와 함께 프로필을 수정하면 200 OK를 반환하고 updateProfile()을 호출한다")
    void updateProfile_withImageKey_Success() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("새닉네임", "profiles/uuid-photo");

        mockMvc.perform(patch("/api/auth/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userProfileUpdateUseCase).updateProfile(any(), eq("새닉네임"), eq("profiles/uuid-photo"));
    }

    @Test
    @WithMockCustomUser
    @DisplayName("profileImageKey 없이 닉네임만 수정하면 200 OK를 반환하고 profileImageKey=null로 updateProfile()을 호출한다")
    void updateProfile_withoutImageKey_Success() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("새닉네임", null);

        mockMvc.perform(patch("/api/auth/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userProfileUpdateUseCase).updateProfile(any(), eq("새닉네임"), isNull());
    }
}
