package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.UpdateUserRequest;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class BetaUserController {
    private final UserService users;
    private final BetaIdentityService identity;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal Jwt jwt,
                              @RequestParam(required = false) UUID userId) {
        return users.getMe(identity.currentUserId(jwt, userId));
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) UUID userId,
                                 @Valid @RequestBody UpdateUserRequest request) {
        return users.updateMe(identity.currentUserId(jwt, userId), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt,
                         @RequestParam(required = false) UUID userId) {
        users.deleteMe(identity.currentUserId(jwt, userId));
    }
}
