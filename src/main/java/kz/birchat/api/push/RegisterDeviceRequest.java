package kz.birchat.api.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "platform обязателен")
        @Pattern(regexp = "(?i)IOS", message = "platform: поддерживается только IOS")
        String platform,

        @NotBlank(message = "token обязателен")
        @Size(max = 512, message = "token слишком длинный")
        @Pattern(regexp = "[0-9a-fA-F]+", message = "token: ожидается hex-строка токена APNs")
        String token,

        @NotBlank(message = "environment обязателен")
        @Pattern(regexp = "production|sandbox", message = "environment: production или sandbox")
        String environment,

        @Pattern(regexp = "ru|kk", message = "locale: ru или kk")
        String locale
) {
}
