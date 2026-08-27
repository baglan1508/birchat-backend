package kz.birchat.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID userId,
        String authorName,
        String authorInitials,
        String type,
        String text,
        LocalDateTime createdAt
) {
}