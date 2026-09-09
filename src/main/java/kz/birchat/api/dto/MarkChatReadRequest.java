package kz.birchat.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MarkChatReadRequest(

        @NotNull(message = "messageId обязателен")
        UUID messageId
) {
}