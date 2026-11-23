package kz.birchat.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDirectChatRequest(
        @NotNull(message = "userId обязателен")
        UUID userId
) {
}
