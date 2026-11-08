package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BetaCreateChatMessageRequest(
        UUID userId,
        @NotBlank @Size(max = 5000) String text
) {}
