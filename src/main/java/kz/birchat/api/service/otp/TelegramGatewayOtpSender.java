package kz.birchat.api.service.otp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.config.TelegramGatewayProperties;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Отправка кода входа через Telegram Gateway API.
 *
 * 1. checkSendAbility — проверяет, что на номер можно отправить код (у номера есть Telegram).
 *    Если нельзя — запрос бесплатный, пользователь получает понятную ошибку.
 * 2. sendVerificationMessage с request_id из шага 1 — повторно не тарифицируется.
 *    Код генерирует наш backend; недоставленные за ttl коды Telegram возвращает на баланс.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otp.provider", havingValue = "telegram")
public class TelegramGatewayOtpSender implements OtpSender {

    private static final String DEFAULT_BASE_URL = "https://gatewayapi.telegram.org";
    private static final int MIN_TTL_SECONDS = 30;
    private static final int MAX_TTL_SECONDS = 3600;

    private final TelegramGatewayProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public OtpSendResult send(String phone, String code, int ttlSeconds) {
        RestClient client = client();

        JsonNode ability = call(client, "checkSendAbility", Map.of("phone_number", phone), true);
        String requestId = ability.path("request_id").asText(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone_number", phone);
        body.put("code", code);
        body.put("ttl", Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, ttlSeconds)));

        if (requestId != null && !requestId.isBlank()) {
            body.put("request_id", requestId);
        }

        if (!isBlank(properties.senderUsername())) {
            body.put("sender_username", properties.senderUsername().trim());
        }

        JsonNode sent = call(client, "sendVerificationMessage", body, false);

        return new OtpSendResult("telegram", sent.path("request_id").asText(requestId));
    }

    /**
     * @param phoneCheck true для checkSendAbility: ошибка там обычно значит, что номер не в Telegram
     */
    private JsonNode call(RestClient client, String method, Map<String, ?> body, boolean phoneCheck) {
        String raw;

        try {
            raw = client.post()
                    .uri("/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            // Telegram может вернуть {"ok":false,"error":"..."} вместе с HTTP 4xx
            raw = ex.getResponseBodyAsString();

            if (raw == null || raw.isBlank()) {
                log.warn("Telegram Gateway {} failed. status={}", method, ex.getStatusCode().value());
                throw unavailable();
            }
        } catch (ResourceAccessException ex) {
            log.warn("Telegram Gateway {} is unreachable: {}", method, ex.getMessage());
            throw unavailable();
        }

        JsonNode response = parse(raw, method);

        if (response.path("ok").asBoolean(false)) {
            return response.path("result");
        }

        String error = response.path("error").asText("UNKNOWN");
        log.warn("Telegram Gateway {} returned error={}", method, error);

        throw toApiException(error, phoneCheck);
    }

    private ApiException toApiException(String error, boolean phoneCheck) {
        String upper = error.toUpperCase();

        boolean serviceProblem = upper.contains("ACCESS_TOKEN")
                || upper.contains("BALANCE")
                || upper.contains("FLOOD")
                || upper.contains("TOO_MANY");

        if (!serviceProblem && (phoneCheck || upper.contains("PHONE"))) {
            return ApiException.badRequest(
                    ApiErrorCode.OTP_DELIVERY_FAILED,
                    "Не удалось отправить код в Telegram. "
                            + "Проверьте, что на этот номер зарегистрирован Telegram"
            );
        }

        return unavailable();
    }

    private JsonNode parse(String raw, String method) {
        try {
            JsonNode node = objectMapper.readTree(raw);

            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("not an object");
            }

            return node;
        } catch (Exception ex) {
            log.warn("Telegram Gateway {} returned unexpected body", method);
            throw unavailable();
        }
    }

    private RestClient client() {
        if (isBlank(properties.accessToken())) {
            log.error("Telegram Gateway is not configured: otp.telegram.access-token is empty");
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.OTP_DELIVERY_DISABLED,
                    "Отправка кодов временно недоступна"
            );
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        String baseUrl = isBlank(properties.baseUrl()) ? DEFAULT_BASE_URL : properties.baseUrl().trim();

        return restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + properties.accessToken().trim())
                .build();
    }

    private ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.OTP_DELIVERY_FAILED,
                "Сервис отправки кодов временно недоступен. Попробуйте позже"
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
