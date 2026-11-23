package kz.birchat.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Новое текстовое сообщение.
 *
 * text — обязателен, кроме пересылки (тогда берётся текст пересылаемого сообщения).
 * replyToMessageId — ответ на сообщение этого же чата.
 * forwardedFromMessageId — пересылка сообщения из доступного чата компании (вложение прикрепляется то же).
 * mentions — userId упомянутых; сервер оставляет только участников чата.
 */
public record CreateChatMessageRequest(

        @Size(max = 5000, message = "text не должен превышать 5000 символов")
        String text,

        UUID replyToMessageId,

        UUID forwardedFromMessageId,

        List<UUID> mentions
) {
}
