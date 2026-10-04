package kz.birchat.api.ai;

import java.util.List;

public record AiSummaryProviderResponse(
        String title,
        String summary,
        List<String> items,
        String model
) {
}