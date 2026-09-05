package kz.birchat.api.service;

import kz.birchat.api.dto.UpdateUserRequest;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        UserEntity user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateUserRequest request) {
        UserEntity user = findUser(userId);

        String fullName = request.fullName().trim();

        user.setFullName(fullName);
        user.setDisplayName(buildDisplayName(fullName));
        user.setInitials(buildInitials(fullName));
        user.setUpdatedAt(LocalDateTime.now());

        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    private UserEntity findUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId обязателен");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                user.getAvatarUrl(),
                user.getIsActive()
        );
    }

    private String buildDisplayName(String fullName) {
        return Arrays.stream(fullName.trim().split("\\s+"))
                .findFirst()
                .orElse(fullName);
    }

    private String buildInitials(String fullName) {
        return Arrays.stream(fullName.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }
}