package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Сводка директора: общая часть и разбивка по чатам (общий чат и группы). */
public record AiDirectorSummaryResponse(
        String title,
        String summary,
        List<String> items,
        OffsetDateTime createdAt,
        List<Section> sections
) {

    public record Section(
            UUID chatId,
            String name,
            String summary
    ) {
    }
}
