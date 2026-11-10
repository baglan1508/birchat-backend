package kz.birchat.api.service.otp;

import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Отправка кодов выключена. Вход возможен только по demo-номеру (он не вызывает отправителя).
 */
@Service
@ConditionalOnProperty(name = "otp.provider", havingValue = "disabled")
public class DisabledOtpSender implements OtpSender {

    @Override
    public OtpSendResult send(String phone, String code, int ttlSeconds) {
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.OTP_DELIVERY_DISABLED,
                "Отправка кодов временно недоступна"
        );
    }
}
