package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.sms")
public record AuthSmsProperties(
        Integer codeTtlSeconds,
        Integer resendCooldownSeconds,
        Integer maxAttempts,
        String demoPhone,
        String demoCode,
        String otpSecret
) {
}