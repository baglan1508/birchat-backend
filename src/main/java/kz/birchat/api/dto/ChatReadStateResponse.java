package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatReadStateResponse(
        UUID chatId,
        UUID userId,
        UUID lastReadMessageId,
        OffsetDateTime lastReadMessageCreatedAt,
        Long unreadCount
) {
}