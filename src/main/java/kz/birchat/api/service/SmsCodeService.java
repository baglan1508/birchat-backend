package kz.birchat.api.service;

import kz.birchat.api.config.AuthSmsProperties;
import kz.birchat.api.entity.AuthCodeEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.AuthCodeRepository;
import kz.birchat.api.service.sms.SmsSendResult;
import kz.birchat.api.service.sms.SmsSender;
import kz.birchat.api.util.PhoneUtils;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private final AuthCodeRepository authCodeRepository;
    private final AuthSmsProperties properties;
    private final SmsSender smsSender;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void sendCode(String rawPhone) {
        String phone = PhoneUtils.normalize(rawPhone);
        LocalDateTime now = TimeUtils.utcNow();

        boolean demoPhone = isDemoPhone(phone);

        if (!demoPhone) {
            checkResendCooldown(phone, now);
        }

        consumePreviousCodes(phone, now);

        String code = demoPhone ? properties.demoCode() : generateCode();
        String message = "Ваш код BirChat: " + code;

        SmsSendResult sendResult = demoPhone
                ? new SmsSendResult("demo", null)
                : smsSender.send(phone, message);

        AuthCodeEntity entity = new AuthCodeEntity();
        entity.setId(UUID.randomUUID());
        entity.setPhone(phone);
        entity.setCodeHash(hashCode(phone, code));
        entity.setAttemptsCount(0);
        entity.setMaxAttempts(maxAttempts());
        entity.setExpiresAt(now.plusSeconds(codeTtlSeconds()));
        entity.setConsumed(false);
        entity.setProvider(sendResult.provider());
        entity.setProviderMessageId(sendResult.providerMessageId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        authCodeRepository.save(entity);
    }

    @Transactional
    public String verifyCode(String rawPhone, String code) {
        String phone = PhoneUtils.normalize(rawPhone);
        LocalDateTime now = TimeUtils.utcNow();

        AuthCodeEntity entity = authCodeRepository
                .findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> ApiException.badRequest(
                        ApiErrorCode.INVALID_CODE,
                        "Код подтверждения не найден"
                ));

        if (Boolean.TRUE.equals(entity.getConsumed())) {
            throw ApiException.badRequest(
                    ApiErrorCode.INVALID_CODE,
                    "Код подтверждения уже использован"
            );
        }

        if (entity.getExpiresAt().isBefore(now)) {
            entity.setConsumed(true);
            entity.setUpdatedAt(now);
            authCodeRepository.save(entity);

            throw ApiException.badRequest(
                    ApiErrorCode.CODE_EXPIRED,
                    "Срок действия кода истёк"
            );
        }

        if (entity.getAttemptsCount() >= entity.getMaxAttempts()) {
            throw ApiException.badRequest(
                    ApiErrorCode.TOO_MANY_ATTEMPTS,
                    "Превышено количество попыток ввода кода"
            );
        }

        boolean valid = entity.getCodeHash().equals(hashCode(phone, code));

        if (!valid) {
            entity.setAttemptsCount(entity.getAttemptsCount() + 1);
            entity.setUpdatedAt(now);

            if (entity.getAttemptsCount() >= entity.getMaxAttempts()) {
                authCodeRepository.save(entity);

                throw ApiException.badRequest(
                        ApiErrorCode.TOO_MANY_ATTEMPTS,
                        "Превышено количество попыток ввода кода"
                );
            }

            authCodeRepository.save(entity);

            throw ApiException.badRequest(
                    ApiErrorCode.INVALID_CODE,
                    "Неверный код подтверждения"
            );
        }

        entity.setConsumed(true);
        entity.setUpdatedAt(now);
        authCodeRepository.save(entity);

        return phone;
    }

    private void checkResendCooldown(String phone, LocalDateTime now) {
        authCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .ifPresent(lastCode -> {
                    LocalDateTime nextAllowedAt =
                            lastCode.getCreatedAt().plusSeconds(resendCooldownSeconds());

                    if (nextAllowedAt.isAfter(now)) {
                        throw ApiException.badRequest(
                                ApiErrorCode.TOO_MANY_ATTEMPTS,
                                "Код уже был отправлен. Повторите позже"
                        );
                    }
                });
    }

    private void consumePreviousCodes(String phone, LocalDateTime now) {
        List<AuthCodeEntity> activeCodes =
                authCodeRepository.findByPhoneAndConsumedFalse(phone);

        activeCodes.forEach(code -> {
            code.setConsumed(true);
            code.setUpdatedAt(now);
        });

        authCodeRepository.saveAll(activeCodes);
    }

    private String generateCode() {
        int value = secureRandom.nextInt(10_000);
        return String.format("%04d", value);
    }

    private String hashCode(String phone, String code) {
        try {
            String secret = properties.otpSecret();

            if (secret == null || secret.isBlank()) {
                throw ApiException.badRequest(
                        ApiErrorCode.BAD_REQUEST,
                        "AUTH_SMS_OTP_SECRET не настроен"
                );
            }

            String source = phone + ":" + code + ":" + secret;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest(
                    ApiErrorCode.BAD_REQUEST,
                    "Ошибка генерации hash кода"
            );
        }
    }

    private boolean isDemoPhone(String phone) {
        return properties.demoPhone() != null
                && !properties.demoPhone().isBlank()
                && phone.equals(PhoneUtils.normalize(properties.demoPhone()));
    }

    private int codeTtlSeconds() {
        return properties.codeTtlSeconds() == null
                ? 300
                : properties.codeTtlSeconds();
    }

    private int resendCooldownSeconds() {
        return properties.resendCooldownSeconds() == null
                ? 60
                : properties.resendCooldownSeconds();
    }

    private int maxAttempts() {
        return properties.maxAttempts() == null
                ? 5
                : properties.maxAttempts();
    }
}