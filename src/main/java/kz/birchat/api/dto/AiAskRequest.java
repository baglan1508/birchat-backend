package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAskRequest(
        @NotBlank(message = "question обязателен")
        @Size(max = 5000, message = "question не должен превышать 5000 символов")
        String question
) {
}