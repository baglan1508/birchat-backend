package kz.birchat.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Сообщение с файлом. replyToMessageId и mentions — как у текстового сообщения. */
public record CreateFileMessageRequest(

        @NotNull(message = "fileId обязателен")
        UUID fileId,

        @Size(max = 5000, message = "text не должен превышать 5000 символов")
        String text,

        UUID replyToMessageId,

        List<UUID> mentions
) {
}
