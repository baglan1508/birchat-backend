package kz.birchat.api.service.otp;

public record OtpSendResult(
        String provider,
        String providerMessageId
) {
}
