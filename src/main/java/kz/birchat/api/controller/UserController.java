package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.UpdateUserRequest;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.service.UserService;
import kz.birchat.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMe(@CurrentUser UUID userId) {
        return userService.getMe(userId);
    }

    @PutMapping("/me")
    public UserResponse updateMe(
            @CurrentUser UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateMe(userId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentUser UUID userId) {
        userService.deleteMe(userId);
    }
}