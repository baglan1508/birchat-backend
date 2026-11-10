package kz.birchat.api.service.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Только для локальной разработки: код пишется в лог. Никогда не включать на сервере.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "otp.provider", havingValue = "console")
public class ConsoleOtpSender implements OtpSender {

    @Override
    public OtpSendResult send(String phone, String code, int ttlSeconds) {
        log.warn("OTP console mode (только для разработки). phone={}, code={}", phone, code);
        return new OtpSendResult("console", null);
    }
}
