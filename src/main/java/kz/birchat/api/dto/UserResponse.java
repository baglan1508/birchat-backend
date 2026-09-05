package kz.birchat.api.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String phone,
        String fullName,
        String displayName,
        String initials,
        String avatarUrl,
        Boolean isActive
) {
}