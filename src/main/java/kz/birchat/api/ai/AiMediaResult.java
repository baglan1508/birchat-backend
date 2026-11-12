package kz.birchat.api.ai;

/** Что модель поняла из вложения: описание фото (позже — расшифровка голосового). */
public record AiMediaResult(
        String text,
        String model
) {
}
