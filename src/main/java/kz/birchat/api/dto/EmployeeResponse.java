package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmployeeResponse(
        UUID memberId,
        UUID userId,
        String fullName,
        String displayName,
        String initials,
        String phone,
        String avatarUrl,
        String role,
        String roleLabel,
        String position,
        String status,
        OffsetDateTime joinedAt
) {
}