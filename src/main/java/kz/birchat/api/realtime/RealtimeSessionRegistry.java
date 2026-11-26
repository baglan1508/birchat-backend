package kz.birchat.api.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Открытые WebSocket-соединения по пользователям (у одного может быть несколько устройств).
 *
 * Хранится в памяти сервера. При нескольких серверах событие дойдёт только до тех, кто подключён
 * к серверу, где оно произошло, — тогда понадобится общий канал (Postgres LISTEN/NOTIFY или Redis).
 */
@Slf4j
@Component
public class RealtimeSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(UUID userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(UUID userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, set) -> {
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }

    public boolean hasSessions(UUID userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public int connectionCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    public List<WebSocketSession> all() {
        return sessions.values().stream().flatMap(Set::stream).toList();
    }

    /** Отправить текст всем соединениям этих пользователей. Сбой одного соединения не мешает остальным. */
    public int send(Collection<UUID> userIds, String json) {
        int sent = 0;

        for (UUID userId : userIds) {
            Set<WebSocketSession> userSessions = sessions.get(userId);

            if (userSessions == null) {
                continue;
            }

            for (WebSocketSession session : userSessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        sent++;
                    }
                } catch (Exception ex) {
                    log.debug("Realtime send failed, session {}: {}", session.getId(), ex.toString());
                }
            }
        }

        return sent;
    }
}
