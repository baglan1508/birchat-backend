package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID userId,
        String authorName,
        String authorInitials,
        String type,
        String text,
        OffsetDateTime createdAt,
        List<ChatAttachmentResponse> attachments,
        ReplyTo replyTo,
        ForwardedFrom forwardedFrom,
        OffsetDateTime editedAt,
        List<Mention> mentions
) {

    /** На что ответили. text — первые 100 символов (у вложения — подпись или имя файла); у удалённого null. */
    public record ReplyTo(
            UUID id,
            UUID userId,
            String authorName,
            String text,
            String type,
            boolean deleted
    ) {
    }

    /** Откуда переслано. */
    public record ForwardedFrom(
            UUID messageId,
            UUID userId,
            String authorName
    ) {
    }

    public record Mention(
            UUID userId,
            String fullName
    ) {
    }
}
