package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatSummaryResponse(
        UUID chatId,
        String type,
        String name,
        Long membersCount,
        Long unreadCount,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt
) {
}
