package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Сообщения чата, изменённые после since; checkedAt — since для следующего запроса. */
public record UpdatedMessagesResponse(
        List<ChatMessageResponse> messages,
        OffsetDateTime checkedAt
) {
}
