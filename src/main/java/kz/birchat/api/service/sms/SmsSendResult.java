package kz.birchat.api.service.sms;

public record SmsSendResult(
        String provider,
        String providerMessageId
) {
}