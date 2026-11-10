package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки access-токенов.
 *
 * secret — минимум 32 байта (HS256). Генерировать, например: openssl rand -base64 48
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String secret,
        Long accessTokenTtlSeconds,
        String issuer
) {
}
