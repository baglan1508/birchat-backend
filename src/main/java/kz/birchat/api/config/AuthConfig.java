package kz.birchat.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AuthSmsProperties.class,
        TelegramGatewayProperties.class
})
public class AuthConfig {
}