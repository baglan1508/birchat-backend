package kz.birchat.api.dto;

import java.util.List;
import java.util.UUID;

/** Реакции на сообщение для события message.reactions: одно событие на всех, поэтому вместо mine — userIds. */
public record ReactionSummary(
        String emoji,
        long count,
        List<UUID> userIds
) {
}
