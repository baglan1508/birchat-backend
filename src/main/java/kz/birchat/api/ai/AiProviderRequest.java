package kz.birchat.api.ai;

import java.util.UUID;

public record AiProviderRequest(
        UUID companyId,
        UUID userId,
        String question
) {
}