package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.auth.CurrentUser;
import kz.birchat.api.dto.AuthResponse;
import kz.birchat.api.dto.RefreshTokenRequest;
import kz.birchat.api.dto.SendCodeRequest;
import kz.birchat.api.dto.SendCodeResponse;
import kz.birchat.api.dto.TokenPairResponse;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.dto.VerifyCodeRequest;
import kz.birchat.api.service.AuthService;
import kz.birchat.api.service.JwtTokenService;
import kz.birchat.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    @PostMapping("/send-code")
    public SendCodeResponse sendCode(
            @Valid @RequestBody SendCodeRequest request
    ) {
        return authService.sendCode(request);
    }

    @PostMapping("/verify-code")
    public AuthResponse verifyCode(
            @Valid @RequestBody VerifyCodeRequest request
    ) {
        return authService.verifyCode(request);
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return jwtTokenService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        jwtTokenService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser UUID userId) {
        return userService.getMe(userId);
    }
}
