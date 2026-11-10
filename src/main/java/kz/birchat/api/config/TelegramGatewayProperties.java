package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram Gateway (https://core.telegram.org/gateway).
 *
 * accessToken — токен из кабинета gateway.telegram.org (переменная TELEGRAM_GATEWAY_TOKEN).
 * senderUsername — необязательно: верифицированный канал, от имени которого придёт код.
 */
@ConfigurationProperties(prefix = "otp.telegram")
public record TelegramGatewayProperties(
        String accessToken,
        String baseUrl,
        String senderUsername
) {
}
