package kz.birchat.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BetaCreateFileMessageRequest(
        UUID userId,
        @NotNull UUID fileId,
        @Size(max = 5000) String text
) {}
