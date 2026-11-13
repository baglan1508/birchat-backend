package kz.birchat.api.ai;

public interface AiProvider {

    String code();

    AiProviderResponse ask(AiProviderRequest request);

    AiSummaryProviderResponse summarize(AiSummaryProviderRequest request);

    /**
     * Описание фото для контекста AI: что изображено и главный текст на нём.
     *
     * @param imageUrl временная ссылка на файл в хранилище (модель скачивает фото сама)
     */
    AiMediaResult describeImage(String imageUrl);

    /**
     * Расшифровка куска голосового (wav, до 30 секунд).
     *
     * @param previousText уже расшифрованное начало записи — помогает не терять слова на стыке кусков
     */
    AiMediaResult transcribeAudio(java.nio.file.Path audioChunk, String previousText);
}