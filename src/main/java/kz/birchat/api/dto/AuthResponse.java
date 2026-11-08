package kz.birchat.api.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UUID userId,
        String phone,
        String fullName,
        String displayName,
        String initials,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {
    public AuthResponse(UUID userId, String phone, String fullName, String displayName,
                        String initials, String accessToken) {
        this(userId, phone, fullName, displayName, initials, accessToken, null, null, null);
    }
}