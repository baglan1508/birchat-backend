package kz.birchat.api.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String phone,
        String fullName,
        String displayName,
        String initials,
        String accessToken
) {
}