package com.example.DunbarHorizon.account.adapter.in.web;

import com.example.DunbarHorizon.account.application.port.in.LoginUseCase;
import com.example.DunbarHorizon.account.application.port.in.SignupUseCase;
import com.example.DunbarHorizon.account.application.port.in.UserProfileUpdateUseCase;
import com.example.DunbarHorizon.account.application.port.in.VerificationUseCase;
import com.example.DunbarHorizon.account.application.dto.AuthTokenResult;
import com.example.DunbarHorizon.account.adapter.in.web.dto.LoginRequestDto;
import com.example.DunbarHorizon.account.adapter.in.web.dto.LogoutRequest;
import com.example.DunbarHorizon.account.adapter.in.web.dto.SignupRequestDto;
import com.example.DunbarHorizon.account.adapter.in.web.dto.UserProfileUpdateRequest;
import com.example.DunbarHorizon.account.adapter.in.web.dto.VerificationEmailRequestDto;
import com.example.DunbarHorizon.account.adapter.in.web.dto.VerificationTokenResponse;
import com.example.DunbarHorizon.account.application.port.out.ProfileImageStoragePort;
import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import com.example.DunbarHorizon.global.imageStorage.PresignedUploadResult;
import com.example.DunbarHorizon.global.security.AuthCookieManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

    private final SignupUseCase signupUseCase;
    private final LoginUseCase loginUseCase;
    private final VerificationUseCase verificationUseCase;
    private final UserProfileUpdateUseCase userProfileUpdateUseCase;
    private final ProfileImageStoragePort profileImageStoragePort;
    private final AuthCookieManager authCookieManager;

    @PostMapping("/users")
    public ResponseEntity<Void> signup(@RequestBody @Valid SignupRequestDto dto,
                                       HttpServletResponse response) {
        AuthTokenResult jwts = signupUseCase.signup(dto.token(), dto.password(), dto.nickname());
        handleTokenResponse(response, jwts);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/tokens")
    public ResponseEntity<Void> login(@RequestBody @Valid LoginRequestDto loginRequestDto,
                                      HttpServletResponse response) {
        AuthTokenResult jwts = loginUseCase.login(loginRequestDto.email(), loginRequestDto.password());
        handleTokenResponse(response, jwts);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/tokens")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @RequestBody(required = false) LogoutRequest logoutRequest,
            HttpServletResponse response) {
        String fcmToken = logoutRequest != null ? logoutRequest.fcmToken() : null;
        loginUseCase.logout(refreshToken, fcmToken);
        authCookieManager.addExpiredTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/tokens")
    public ResponseEntity<Void> reissue(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        AuthTokenResult newJwts = loginUseCase.reissue(refreshToken);
        handleTokenResponse(response, newJwts);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verifications")
    public ResponseEntity<Void> requestVerification(
            @RequestBody @Valid VerificationEmailRequestDto verificationEmailRequestDto) {
        verificationUseCase.requestVerification(
                verificationEmailRequestDto.email(), verificationEmailRequestDto.redirectPage());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 토큰 유효성 확인. 프론트가 비밀번호 입력 폼을 그리기 전에 호출한다.
     * 없으면 사용자가 폼을 다 채워 제출한 뒤에야 만료를 알게 된다.
     */
    @GetMapping("/verifications/{token}")
    public ResponseEntity<VerificationTokenResponse> resolveVerification(@PathVariable String token) {
        return ResponseEntity.ok(new VerificationTokenResponse(verificationUseCase.resolveEmail(token)));
    }

    @PostMapping("/users/me/profile-image/presign")
    public ResponseEntity<PresignedUploadResult> presignProfileImage(
            @CurrentUserId Long userId,
            @RequestParam String contentType) {
        return ResponseEntity.ok(profileImageStoragePort.presignUpload(contentType));
    }

    @PatchMapping("/users/me")
    public ResponseEntity<Void> updateProfile(
            @CurrentUserId Long userId,
            @RequestBody @Valid UserProfileUpdateRequest request) {
        userProfileUpdateUseCase.updateProfile(userId, request.nickname(), request.profileImageKey());
        return ResponseEntity.ok().build();
    }

    private void handleTokenResponse(HttpServletResponse response, AuthTokenResult tokens) {
        authCookieManager.addAccessTokenCookie(response, tokens.accessToken());
        authCookieManager.addRefreshTokenCookie(response, tokens.refreshToken());
    }
}
