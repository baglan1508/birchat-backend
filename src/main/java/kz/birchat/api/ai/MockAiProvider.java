package kz.birchat.api.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockAiProvider implements AiProvider {

    private static final String CODE = "mock";
    private static final String MODEL = "mock";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public AiProviderResponse ask(AiProviderRequest request) {
        int contextMessagesCount = request.contextMessages() == null
                ? 0
                : request.contextMessages().size();

        String answer = """
                AI mock: я пока работаю в тестовом режиме.
                Контекст последних сообщений общего чата получен.

                Количество сообщений в контексте: %s

                Ваш вопрос: %s
                """.formatted(
                contextMessagesCount,
                request.question()
        );

        return new AiProviderResponse(
                answer,
                MODEL
        );
    }

    @Override
    public AiSummaryProviderResponse summarize(AiSummaryProviderRequest request) {
        int contextMessagesCount = request.contextMessages() == null
                ? 0
                : request.contextMessages().size();

        return new AiSummaryProviderResponse(
                "Сводка за сегодня",
                "AI mock: тестовая сводка по сообщениям общего чата.",
                List.of(
                        "Количество сообщений в контексте: " + contextMessagesCount,
                        "Период: " + request.periodStart() + " — " + request.periodEnd(),
                        "Реальная сводка будет сформирована при AI_PROVIDER=glm"
                ),
                MODEL
        );
    }
}