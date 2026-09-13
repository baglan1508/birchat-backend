package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiDirectorSummaryResponse(
        String title,
        String summary,
        List<String> items,
        OffsetDateTime createdAt
) {
}