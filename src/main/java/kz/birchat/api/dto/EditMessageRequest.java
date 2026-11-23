package kz.birchat.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Изменить сообщение (только автор, не позже 48 часов). У сообщения с файлом меняется подпись.
 * mentions — если передано, заменяет список упомянутых.
 */
public record EditMessageRequest(

        @Size(max = 5000, message = "text не должен превышать 5000 символов")
        String text,

        List<UUID> mentions
) {
}
