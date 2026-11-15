package kz.birchat.api.push;

import kz.birchat.api.enums.ChatMessageType;

/**
 * Текст уведомления: «Имя: текст» (текст до 100 символов) или подпись вложения.
 */
public final class PushTextBuilder {

    static final int MAX_TEXT_LENGTH = 100;

    private PushTextBuilder() {
    }

    /**
     * @param fileName имя файла первого вложения (для документа)
     * @param locale   язык устройства: ru или kk
     */
    public static String body(String authorName, ChatMessageType type, String content, String fileName, String locale) {
        String author = authorName == null || authorName.isBlank() ? "BirChat" : authorName.trim();
        String text = stripQuoteLine(content);

        if (text.isEmpty()) {
            text = attachmentLabel(type, fileName, locale);
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH).trim() + "…";
        }

        return author + ": " + text;
    }

    /**
     * Ответы и пересылки приложение кладёт первой строкой текста: «> цитата» (ответ)
     * или «>> от кого» (пересылка). В уведомлении эта строка не нужна.
     * Строка «> …» без переноса после неё — обычный текст, её не трогаем.
     */
    static String stripQuoteLine(String content) {
        if (content == null) {
            return "";
        }

        String text = content.strip();

        if (!text.startsWith(">")) {
            return text;
        }

        int lineEnd = text.indexOf('\n');

        if (lineEnd < 0) {
            // пересылка без подписи (переслан файл) — текста нет; одиночная «> строка» — обычный текст
            return text.startsWith(">>") ? "" : text;
        }

        return text.substring(lineEnd + 1).strip();
    }

    static String attachmentLabel(ChatMessageType type, String fileName, String locale) {
        boolean kazakh = "kk".equals(locale);

        if (type == ChatMessageType.IMAGE) {
            return kazakh ? "📷 Фотосурет" : "📷 Фото";
        }

        if (type == ChatMessageType.VOICE) {
            return kazakh ? "🎤 Дауыстық хабарлама" : "🎤 Голосовое сообщение";
        }

        if (type == ChatMessageType.DOCUMENT) {
            return "📎 " + (fileName == null || fileName.isBlank() ? (kazakh ? "Файл" : "Файл") : fileName.trim());
        }

        return kazakh ? "Жаңа хабарлама" : "Новое сообщение";
    }
}
