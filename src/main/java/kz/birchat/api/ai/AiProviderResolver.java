package kz.birchat.api.ai;

import kz.birchat.api.config.AiProperties;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiProviderResolver {

    private final AiProperties aiProperties;
    private final List<AiProvider> providers;

    public AiProvider resolve() {
        String providerCode = aiProperties.getProvider();

        if (providerCode == null || providerCode.isBlank()) {
            providerCode = "mock";
        }

        String normalizedCode = providerCode.trim().toLowerCase();

        return providers.stream()
                .filter(provider -> provider.code().equalsIgnoreCase(normalizedCode))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        ApiErrorCode.BAD_REQUEST,
                        "Неизвестный AI provider: " + normalizedCode
                ));
    }
}