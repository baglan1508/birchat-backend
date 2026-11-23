package kz.birchat.api.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging (пуши на Android) через HTTP v1 API.
 *
 * serviceAccountJson — JSON-ключ сервисного аккаунта Firebase целиком (переменная FCM_SERVICE_ACCOUNT_JSON):
 * из него берутся project_id, client_email и private_key. Пока он не задан, пуши на Android не отправляются.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "push.fcm")
public class FcmProperties {

    private String serviceAccountJson = "";

    public boolean isConfigured() {
        return serviceAccountJson != null && !serviceAccountJson.isBlank();
    }
}
