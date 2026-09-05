package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.UpdateUserRequest;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMe(@RequestParam UUID userId) {
        return userService.getMe(userId);
    }

    @PutMapping("/me")
    public UserResponse updateMe(
            @RequestParam UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateMe(userId, request);
    }
}