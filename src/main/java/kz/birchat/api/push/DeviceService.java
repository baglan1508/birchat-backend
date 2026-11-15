package kz.birchat.api.push;

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
        String token = request.token().toLowerCase(Locale.ROOT);
        LocalDateTime now = TimeUtils.utcNow();

        DeviceTokenEntity device = deviceTokenRepository.findByToken(token).orElseGet(() -> {
            DeviceTokenEntity created = new DeviceTokenEntity();
            created.setId(UUID.randomUUID());
            created.setToken(token);
            created.setCreatedAt(now);
            return created;
        });

        device.setUserId(userId);
        device.setPlatform(request.platform().toUpperCase(Locale.ROOT));
        device.setEnvironment(request.environment());
        device.setLocale(request.locale() == null ? "ru" : request.locale());
        device.setUpdatedAt(now);

        deviceTokenRepository.save(device);
    }

    /**
     * Выход из аккаунта на устройстве. Удаляется только свой токен; неизвестный токен — не ошибка.
     */
    @Transactional
    public void unregister(UUID userId, String token) {
        deviceTokenRepository.findByToken(token.toLowerCase(Locale.ROOT))
                .filter(device -> userId.equals(device.getUserId()))
                .ifPresent(deviceTokenRepository::delete);
    }

    /** Удаление аккаунта: все устройства пользователя. */
    @Transactional
    public void unregisterAll(UUID userId) {
        deviceTokenRepository.deleteByUser(userId);
    }
}
