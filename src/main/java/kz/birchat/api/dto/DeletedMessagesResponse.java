package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сообщения чата, удалённые у всех после момента since.
 * checkedAt передаётся как since в следующем запросе.
 */
public record DeletedMessagesResponse(
        List<UUID> messageIds,
        OffsetDateTime checkedAt
) {
}
