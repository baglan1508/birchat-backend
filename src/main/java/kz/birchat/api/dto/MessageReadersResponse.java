package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Кто прочитал сообщение: readCount из recipientsCount (автор не считается). */
public record MessageReadersResponse(
        UUID messageId,
        int readCount,
        int recipientsCount,
        List<Reader> readers
) {

    /** lastReadAt — когда пользователь последний раз отмечал чат прочитанным. */
    public record Reader(
            UUID userId,
            String displayName,
            String initials,
            String avatarUrl,
            OffsetDateTime lastReadAt
    ) {
    }
}
