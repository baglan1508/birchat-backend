package kz.birchat.api.ai;

public record AiProviderResponse(
        String answer,
        String model
) {
}