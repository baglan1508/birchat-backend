package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.AuthResponse;
import kz.birchat.api.dto.SendCodeRequest;
import kz.birchat.api.dto.SendCodeResponse;
import kz.birchat.api.dto.VerifyCodeRequest;
import kz.birchat.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

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
}