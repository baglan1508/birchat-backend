package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUploadUrlRequest(

        @NotBlank(message = "fileName обязателен")
        @Size(max = 255, message = "fileName не должен превышать 255 символов")
        String fileName,

        @NotBlank(message = "contentType обязателен")
        @Size(max = 100, message = "contentType не должен превышать 100 символов")
        String contentType,

        @NotNull(message = "fileSize обязателен")
        @Positive(message = "fileSize должен быть больше 0")
        Long fileSize,

        /* Групповой чат, для которого загружается файл. null — файл компании. */
        UUID chatId
) {
}
