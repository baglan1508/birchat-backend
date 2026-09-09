package kz.birchat.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFileMessageRequest(

        @NotNull(message = "userId обязателен")
        UUID userId,

        @NotNull(message = "fileId обязателен")
        UUID fileId,

        @Size(max = 5000, message = "text не должен превышать 5000 символов")
        String text
) {
}