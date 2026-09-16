package kz.birchat.api.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GlmAiProvider implements AiProvider {

    private static final String CODE = "glm";

    private final AiProperties aiProperties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public AiProviderResponse ask(AiProviderRequest request) {
        AiProperties.Glm glm = aiProperties.getGlm();

        validateConfig(glm);

        RestClient restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(glm.getBaseUrl()))
                .build();

        GlmChatRequest body = new GlmChatRequest(
                glm.getModel(),
                List.of(
                        new GlmMessage(
                                "system",
                                buildSystemPrompt()
                        ),
                        new GlmMessage(
                                "user",
                                request.question()
                        )
                ),
                glm.getMaxTokens(),
                glm.getTemperature()
        );

        try {
            GlmChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + glm.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(GlmChatResponse.class);

            String answer = extractAnswer(response);

            return new AiProviderResponse(
                    answer,
                    glm.getModel()
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI provider временно недоступен"
            );
        }
    }

    private void validateConfig(AiProperties.Glm glm) {
        if (glm == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI GLM настройки не заданы"
            );
        }

        if (glm.getApiKey() == null || glm.getApiKey().isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI_GLM_API_KEY не настроен"
            );
        }

        if (glm.getModel() == null || glm.getModel().isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI_GLM_MODEL не настроен"
            );
        }

        if (glm.getBaseUrl() == null || glm.getBaseUrl().isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI_GLM_BASE_URL не настроен"
            );
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl.trim();

        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }

    private String buildSystemPrompt() {
        return """
                Ты AI Assistant внутри корпоративного приложения BirChat.
                Отвечай на русском языке.
                Отвечай кратко, понятно и по делу.
                Если данных недостаточно, честно скажи, что данных недостаточно.
                Сейчас у тебя ещё нет доступа к сообщениям, файлам и памяти компании.
                Не выдумывай факты о компании.
                """;
    }

    private String extractAnswer(GlmChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI provider вернул пустой ответ"
            );
        }

        GlmChoice firstChoice = response.choices().get(0);

        if (firstChoice == null
                || firstChoice.message() == null
                || firstChoice.message().content() == null
                || firstChoice.message().content().isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "AI provider вернул пустой текст ответа"
            );
        }

        return firstChoice.message().content().trim();
    }

    private record GlmChatRequest(
            String model,
            List<GlmMessage> messages,
            @JsonProperty("max_tokens")
            Integer maxTokens,
            Double temperature
    ) {
    }

    private record GlmMessage(
            String role,
            String content
    ) {
    }

    private record GlmChatResponse(
            List<GlmChoice> choices
    ) {
    }

    private record GlmChoice(
            GlmMessage message
    ) {
    }
}