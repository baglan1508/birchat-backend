package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiHistoryMessageResponse(
        UUID id,
        String role,
        String content,
        String model,
        OffsetDateTime createdAt
) {
}