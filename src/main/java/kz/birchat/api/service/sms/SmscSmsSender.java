package kz.birchat.api.service.sms;

import kz.birchat.api.config.SmscProperties;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "smsc")
public class SmscSmsSender implements SmsSender {

    private final SmscProperties properties;

    @Override
    public SmsSendResult send(String phone, String message) {
        validateConfig();

        String smsPhone = phone.replace("+", "");

        try {
            String response = RestClient.create()
                    .get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("smsc.kz")
                                .path("/sys/send.php")
                                .queryParam("phones", smsPhone)
                                .queryParam("mes", message)
                                .queryParam("charset", "utf-8")
                                .queryParam("fmt", "3");

                        if (!isBlank(properties.apiKey())) {
                            builder.queryParam("apikey", properties.apiKey().trim());
                        } else {
                            builder.queryParam("login", properties.login().trim());
                            builder.queryParam("psw", properties.password().trim());
                        }

                        if (!isBlank(properties.sender())) {
                            builder.queryParam("sender", properties.sender().trim());
                        }

                        return builder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw ApiException.badRequest(
                        ApiErrorCode.BAD_REQUEST,
                        "SMSC вернул пустой ответ"
                );
            }

            if (response.contains("\"error\"") || response.startsWith("ERROR")) {
                throw ApiException.badRequest(
                        ApiErrorCode.BAD_REQUEST,
                        "SMSC ошибка: " + response
                );
            }

            return new SmsSendResult("smsc", response);
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest(
                    ApiErrorCode.BAD_REQUEST,
                    "Ошибка отправки SMS через SMSC: "
                            + ex.getStatusCode()
                            + ". Body: "
                            + ex.getResponseBodyAsString()
            );
        }
    }

    private void validateConfig() {
        boolean hasLoginPassword = !isBlank(properties.login()) && !isBlank(properties.password());
        boolean hasApiKey = !isBlank(properties.apiKey());

        if (!hasLoginPassword && !hasApiKey) {
            throw ApiException.badRequest(
                    ApiErrorCode.BAD_REQUEST,
                    "SMSC не настроен: укажите SMSC_LOGIN/SMSC_PASSWORD или SMSC_API_KEY"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}