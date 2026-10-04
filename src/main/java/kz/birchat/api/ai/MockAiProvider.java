package kz.birchat.api.ai;

import org.springframework.stereotype.Component;

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
}