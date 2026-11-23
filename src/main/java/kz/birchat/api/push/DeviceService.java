package kz.birchat.api.push;

import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Регистрация устройства. Повторная регистрация того же токена обновляет язык, окружение
     * и владельца: если на телефоне вошёл другой пользователь, пуши пойдут ему, а не прежнему.
     */
    @Transactional
    public void register(UUID userId, RegisterDeviceRequest request) {
        String platform = request.platform().toUpperCase(Locale.ROOT);
        boolean android = PLATFORM_ANDROID.equals(platform);
        String token = normalizeToken(platform, request.token());
        String environment = android ? "production" : request.environment();

        if (environment == null || environment.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "environment обязателен для IOS: production или sandbox");
        }

        LocalDateTime now = TimeUtils.utcNow();

        DeviceTokenEntity device = deviceTokenRepository.findByToken(token).orElseGet(() -> {
            DeviceTokenEntity created = new DeviceTokenEntity();
            created.setId(UUID.randomUUID());
            created.setToken(token);
            created.setCreatedAt(now);
            return created;
        });

        device.setUserId(userId);
        device.setPlatform(platform);
        device.setEnvironment(environment);
        device.setLocale(request.locale() == null ? "ru" : request.locale());
        device.setUpdatedAt(now);

        deviceTokenRepository.save(device);
    }

    public static final String PLATFORM_IOS = "IOS";
    public static final String PLATFORM_ANDROID = "ANDROID";

    /** Токен APNs — hex (храним в нижнем регистре); токен FCM чувствителен к регистру — как есть. */
    static String normalizeToken(String platform, String token) {
        String value = token == null ? "" : token.trim();

        if (PLATFORM_ANDROID.equals(platform)) {
            if (!value.matches("[A-Za-z0-9_:\\-]+")) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION, "token: ожидается токен FCM");
            }

            return value;
        }

        if (!value.matches("[0-9a-fA-F]+")) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "token: ожидается hex-строка токена APNs");
        }

        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Выход из аккаунта на устройстве. Удаляется только свой токен; неизвестный токен — не ошибка.
     */
    @Transactional
    public void unregister(UUID userId, String token) {
        // Токен FCM хранится как есть, токен APNs — в нижнем регистре.
        deviceTokenRepository.findByToken(token)
                .or(() -> deviceTokenRepository.findByToken(token.toLowerCase(Locale.ROOT)))
                .filter(device -> userId.equals(device.getUserId()))
                .ifPresent(deviceTokenRepository::delete);
    }

    /** Удаление аккаунта: все устройства пользователя. */
    @Transactional
    public void unregisterAll(UUID userId) {
        deviceTokenRepository.deleteByUser(userId);
    }
}
