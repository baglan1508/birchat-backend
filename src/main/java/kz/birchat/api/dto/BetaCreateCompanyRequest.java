package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** ownerUserId is optional for older Flutter clients; JWT is authoritative. */
public record BetaCreateCompanyRequest(
        UUID ownerUserId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String field,
        String logoUrl,
        String color
) {}
