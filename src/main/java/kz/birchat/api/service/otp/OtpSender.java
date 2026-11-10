package kz.birchat.api.service.otp;

/**
 * Канал доставки одноразового кода входа. Код генерирует и проверяет SmsCodeService,
 * отправитель только доставляет его пользователю.
 */
public interface OtpSender {

    OtpSendResult send(String phone, String code, int ttlSeconds);
}
