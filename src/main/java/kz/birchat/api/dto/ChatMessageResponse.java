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
        List<Mention> mentions,
        List<Reaction> reactions
) {

    /** Реакции: сколько раз поставлен каждый эмодзи и есть ли среди них реакция того, кто смотрит. */
    public record Reaction(
            String emoji,
            long count,
            boolean mine
    ) {
    }

    /** То же сообщение без отметок «моя реакция» — для событий, которые получают все участники. */
    public ChatMessageResponse withoutMine() {
        if (reactions == null || reactions.stream().noneMatch(Reaction::mine)) {
            return this;
        }

        return new ChatMessageResponse(id, userId, authorName, authorInitials, type, text, createdAt, attachments,
                replyTo, forwardedFrom, editedAt, mentions,
                reactions.stream().map(r -> new Reaction(r.emoji(), r.count(), false)).toList());
    }

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
