package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChatMessageRequest(

        @NotBlank(message = "text не должен быть пустым")
        @Size(max = 5000, message = "text не должен превышать 5000 символов")
        String text
) {
}