package com.example.GooRoomBe.account.user.api;

import com.example.GooRoomBe.account.auth.application.AuthService;
import com.example.GooRoomBe.account.user.api.dto.UserSignupRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<Void> signUp(@RequestBody @Valid UserSignupRequestDto userSignupRequestDto) {
        authService.signUp(userSignupRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
