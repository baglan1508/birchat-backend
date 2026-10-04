package kz.birchat.api.ai;

    public record AiProviderMessage(
        String source,
        String author,
        String content,
        String createdAt
) {
}