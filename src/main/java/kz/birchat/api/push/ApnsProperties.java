package kz.birchat.api.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Доступ к Apple Push Notification service по ключу .p8 (token-based).
 *
 * privateKey — содержимое файла AuthKey_XXXXXXXXXX.p8 целиком (переменная APNS_PRIVATE_KEY),
 * keyId — Key ID этого ключа (APNS_KEY_ID). Пока они не заданы, пуши не отправляются.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "push.apns")
public class ApnsProperties {

    private String keyId = "";
    private String teamId = "S59B8XNQ5Z";
    private String bundleId = "kz.birchat.app";
    private String privateKey = "";

    public boolean isConfigured() {
        return notBlank(keyId) && notBlank(teamId) && notBlank(bundleId) && notBlank(privateKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
