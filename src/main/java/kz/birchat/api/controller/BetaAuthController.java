package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.RefreshTokenRequest;
import kz.birchat.api.dto.TokenPairResponse;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class BetaAuthController {
    private final JwtTokenService tokenService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> ApiException.forbidden(ApiErrorCode.FORBIDDEN, "Пользователь неактивен"));
        return Map.of("userId", userId.toString(), "phone", user.getPhone());
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.logout(request.refreshToken());
    }
}
