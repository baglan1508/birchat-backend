package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms.smsc")
public record SmscProperties(
        String login,
        String password,
        String apiKey,
        String sender
) {
}