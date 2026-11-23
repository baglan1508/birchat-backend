package kz.birchat.api.push;

import jakarta.validation.Valid;
import kz.birchat.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Устройства для push-уведомлений. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final PushNotificationService pushNotificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @CurrentUser UUID userId,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        deviceService.register(userId, request);
    }

    /**
     * Тестовый пуш на все свои устройства. Отвечает, что сказали Apple / Google по каждому:
     * SENT — принято к доставке; INVALID_TOKEN — токен недействителен (устройство удалено);
     * FAILED — другая ошибка (status и reason); NOT_CONFIGURED — на сервере нет ключа.
     */
    @PostMapping("/test")
    public List<PushNotificationService.TestPushResult> sendTest(@CurrentUser UUID userId) {
        return pushNotificationService.sendTest(userId);
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            @CurrentUser UUID userId,
            @PathVariable String token
    ) {
        deviceService.unregister(userId, token);
    }
}
