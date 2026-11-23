package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddReactionRequest(
        @NotBlank(message = "emoji обязателен")
        String emoji
) {
}
