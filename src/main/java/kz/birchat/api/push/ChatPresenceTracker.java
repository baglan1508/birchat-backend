package kz.birchat.api.push;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кто сейчас смотрит чат. Приложение опрашивает сообщения открытого чата каждые 5 секунд;
 * если запрос был меньше 15 секунд назад — считаем, что чат открыт, и пуш не шлём.
 *
 * Хранится в памяти сервера: при нескольких серверах или сразу после перезапуска человек,
 * у которого чат открыт, может получить лишний пуш — это допустимо.
 */
@Component
public class ChatPresenceTracker {

    static final Duration OPEN_CHAT_WINDOW = Duration.ofSeconds(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** Пользователь запросил сообщения чата. */
    public void touchChat(UUID userId, UUID chatId) {
        touch(userId + ":" + chatId, Instant.now());
    }

    /** Пользователь запросил сообщения общего чата по старому адресу /chats/general/messages. */
    public void touchGeneralChat(UUID userId, UUID companyId) {
        touch(userId + ":general:" + companyId, Instant.now());
    }

    public boolean isInChat(UUID userId, UUID chatId, UUID companyIdIfGeneral) {
        return isInChat(userId, chatId, companyIdIfGeneral, Instant.now());
    }

    boolean isInChat(UUID userId, UUID chatId, UUID companyIdIfGeneral, Instant now) {
        return isFresh(lastSeen.get(userId + ":" + chatId), now)
                || (companyIdIfGeneral != null
                && isFresh(lastSeen.get(userId + ":general:" + companyIdIfGeneral), now));
    }

    void touch(String key, Instant at) {
        if (lastSeen.size() > CLEANUP_THRESHOLD) {
            Instant cutoff = at.minus(OPEN_CHAT_WINDOW);
            lastSeen.values().removeIf(seen -> seen.isBefore(cutoff));
        }

        lastSeen.put(key, at);
    }

    private static boolean isFresh(Instant seen, Instant now) {
        return seen != null && Duration.between(seen, now).compareTo(OPEN_CHAT_WINDOW) < 0;
    }
}
