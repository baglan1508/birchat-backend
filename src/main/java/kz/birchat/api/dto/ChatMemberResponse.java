package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMemberResponse(
        UUID userId,
        String fullName,
        String displayName,
        String initials,
        String avatarUrl,
        String role,
        String position,
        OffsetDateTime addedAt
) {
}
