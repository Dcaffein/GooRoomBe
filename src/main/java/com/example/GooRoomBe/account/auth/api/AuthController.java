package com.example.GooRoomBe.account.auth.api;

import com.example.GooRoomBe.account.auth.api.dto.VerificationEmailSendRequestDto;
import com.example.GooRoomBe.account.auth.application.AuthService;
import com.example.GooRoomBe.account.auth.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;

    @GetMapping("/email-verifications")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<String> sendVerificationEmail(@RequestBody @Valid VerificationEmailSendRequestDto verificationEmailSendRequestDto) {
        authService.sendVerificationEmail(verificationEmailSendRequestDto.email(), verificationEmailSendRequestDto.redirectPage());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tokens")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails,
                                       HttpServletResponse response) {

        String userId = userDetails.getUsername();
        authService.logout(userId);

        ResponseCookie expiredAccess = authCookieFactory.createExpiredCookie("accessToken");
        ResponseCookie expiredRefresh = authCookieFactory.createExpiredCookie("refreshToken");

        response.addHeader(HttpHeaders.SET_COOKIE, expiredAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expiredRefresh.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/tokens")
    public ResponseEntity<String> reissueTokens(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenValue) {

        if (refreshTokenValue == null) {
            return ResponseEntity.badRequest().body("Refresh Token이 없습니다.");
        }

        Map<String, String> tokens = authService.reissueTokens(refreshTokenValue);

        ResponseCookie accessCookie = authCookieFactory.createAccessTokenCookie(tokens.get("accessToken"));
        ResponseCookie refreshCookie = authCookieFactory.createRefreshTokenCookie(tokens.get("refreshToken"));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }
}

