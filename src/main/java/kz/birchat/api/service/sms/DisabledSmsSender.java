package kz.birchat.api.service.sms;

import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Safe beta default until Telegram Gateway is configured.
 * Demo OTP is handled by SmsCodeService without calling this sender.
 */
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "disabled")
public class DisabledSmsSender implements SmsSender {

    @Override
    public SmsSendResult send(String phone, String message) {
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.OTP_DELIVERY_DISABLED,
                "Отправка кодов на бете временно недоступна"
        );
    }
}
