package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchResultResponse(
        String type,
        UUID id,
        String title,
        String text,
        OffsetDateTime createdAt,
        UUID messageId,
        UUID fileId
) {
}