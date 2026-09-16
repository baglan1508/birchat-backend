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
        String answer = """
                AI mock: я пока работаю в тестовом режиме.
                Позже здесь будет ответ на основе сообщений, файлов и памяти компании.

                Ваш вопрос: %s
                """.formatted(request.question());

        return new AiProviderResponse(
                answer,
                MODEL
        );
    }
}