package kz.birchat.api.ai;

import java.util.List;

/** sections — коротко по каждому чату: номер чата из запроса и текст. */
public record AiSummaryProviderResponse(
        String title,
        String summary,
        List<String> items,
        String model,
        List<Section> sections
) {

    public AiSummaryProviderResponse(String title, String summary, List<String> items, String model) {
        this(title, summary, items, model, List.of());
    }

    public record Section(
            int chatNumber,
            String summary
    ) {
    }
}
