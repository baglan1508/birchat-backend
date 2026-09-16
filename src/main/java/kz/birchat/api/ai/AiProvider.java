package kz.birchat.api.ai;

public interface AiProvider {

    String code();

    AiProviderResponse ask(AiProviderRequest request);
}