package kz.birchat.api.dto;

import java.time.OffsetDateTime;

public record AiAskResponse(
        String answer,
        String model,
        OffsetDateTime createdAt
) {
}