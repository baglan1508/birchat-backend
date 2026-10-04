package kz.birchat.api.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AiSummaryProviderRequest(
        UUID companyId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        List<AiProviderMessage> contextMessages
) {
}