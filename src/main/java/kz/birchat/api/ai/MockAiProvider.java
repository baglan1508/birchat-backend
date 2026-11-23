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
                Последние сообщения:
                %s

                Ваш вопрос: %s
                """.formatted(
                contextMessagesCount,
                lastMessages(request.contextMessages()),
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
                "AI mock: тестовая сводка по сообщениям общего чата и групп.",
                List.of(
                        "Количество сообщений в контексте: " + contextMessagesCount,
                        "Период: " + request.periodStart() + " — " + request.periodEnd(),
                        "Реальная сводка будет сформирована при AI_PROVIDER=glm"
                ),
                MODEL,
                request.chats() == null ? List.of() : request.chats().stream()
                        .map(chat -> new AiSummaryProviderResponse.Section(
                                chat.number(),
                                "AI mock: сообщений в «" + chat.name() + "»: " + countInChat(request, chat.number())
                        ))
                        .toList()
        );
    }

    private static long countInChat(AiSummaryProviderRequest request, int number) {
        if (request.contextMessages() == null) {
            return 0;
        }

        String prefix = "#" + number + " ";
        return request.contextMessages().stream()
                .filter(message -> message.source() != null && message.source().startsWith(prefix))
                .count();
    }

    /** Последние сообщения контекста — чтобы на тесте было видно, что именно получил AI. */
    private static String lastMessages(List<AiProviderMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "—";
        }

        return messages.stream()
                .skip(Math.max(0, messages.size() - 5))
                .map(message -> "- (" + message.source() + ") " + message.author() + ": " + message.content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    public AiMediaResult transcribeAudio(java.nio.file.Path audioChunk, String previousText) {
        return new AiMediaResult(
                "AI mock: тестовая расшифровка (" + audioChunk.getFileName() + ")",
                MODEL
        );
    }

    @Override
    public AiMediaResult describeImage(String imageUrl) {
        return new AiMediaResult(
                "AI mock: тестовое описание фото (настоящее будет при AI_PROVIDER=glm)",
                MODEL
        );
    }
}