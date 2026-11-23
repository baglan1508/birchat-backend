package kz.birchat.api.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * platform — IOS (токен APNs, hex) или ANDROID (токен FCM).
 * environment — для IOS: production (TestFlight, App Store) или sandbox (Xcode); для ANDROID не нужен.
 */
public record RegisterDeviceRequest(
        @NotBlank(message = "platform обязателен")
        @Pattern(regexp = "(?i)IOS|ANDROID", message = "platform: IOS или ANDROID")
        String platform,

        @NotBlank(message = "token обязателен")
        @Size(max = 512, message = "token слишком длинный")
        String token,

        @Pattern(regexp = "production|sandbox", message = "environment: production или sandbox")
        String environment,

        @Pattern(regexp = "ru|kk", message = "locale: ru или kk")
        String locale
) {
}
