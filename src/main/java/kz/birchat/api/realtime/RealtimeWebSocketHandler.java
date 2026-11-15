package kz.birchat.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.push.ChatPresenceTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket /ws. Протокол (JSON-сообщения):
 *
 * клиент → сервер:
 *   {"type":"auth","accessToken":"…"}   — первым сообщением, в течение 10 секунд;
 *   {"type":"viewing","chatId":"…"}     — раз в ~10 секунд, пока чат открыт (чтобы не слать push);
 *   {"type":"ping"}                     — по желанию, ответ {"type":"pong"}.
 *
 * сервер → клиент:
 *   {"type":"auth.ok","userId":"…","expiresAt":"…"}, затем события (см. RealtimeEventService).
 *
 * Токен в адресе не передаётся (адреса попадают в логи). Когда access-токен истекает, сервер
 * закрывает соединение с кодом 4401 — приложение обновляет токен и подключается заново.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    /** Не вошёл или токен истёк — нужно получить новый access-токен и подключиться заново. */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    private static final String ATTR_USER = "userId";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 256 * 1024;

    private final JwtDecoder jwtDecoder;
    private final CurrentUserService currentUserService;
    private final RealtimeSessionRegistry registry;
    private final ChatPresenceTracker presenceTracker;
    private final ObjectMapper objectMapper;

    /** Обёртки соединений: отправка из разных потоков по одному соединению должна идти по очереди. */
    private final Map<String, WebSocketSession> safeSessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ws-timer");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        safeSessions.put(session.getId(), safe);

        timer.schedule(() -> {
            if (userOf(safe) == null) {
                close(safe, UNAUTHORIZED);
            }
        }, AUTH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        WebSocketSession session = safeSessions.getOrDefault(rawSession.getId(), rawSession);
        JsonNode json;

        try {
            json = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            send(session, Map.of("type", "error", "message", "Ожидается JSON"));
            return;
        }

        String type = json.path("type").asText("");

        if ("auth".equals(type)) {
            authenticate(session, json.path("accessToken").asText(""));
            return;
        }

        UUID userId = userOf(session);

        if (userId == null) {
            close(session, UNAUTHORIZED);
            return;
        }

        switch (type) {
            case "ping" -> send(session, Map.of("type", "pong"));
            case "viewing" -> viewing(userId, json.path("chatId").asText(""));
            default -> send(session, Map.of("type", "error", "message", "Неизвестный type: " + type));
        }
    }

    void authenticate(WebSocketSession session, String accessToken) {
        final UUID userId;
        final Instant expiresAt;

        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            userId = currentUserService.currentUserId(jwt);
            expiresAt = jwt.getExpiresAt();
        } catch (RuntimeException ex) {
            close(session, UNAUTHORIZED);
            return;
        }

        UUID previous = userOf(session);

        if (previous != null) {
            registry.remove(previous, session);
        }

        session.getAttributes().put(ATTR_USER, userId);
        registry.add(userId, session);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("type", "auth.ok");
        ok.put("userId", userId.toString());
        ok.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        send(session, ok);

        if (expiresAt != null) {
            long delay = Math.max(0, Duration.between(Instant.now(), expiresAt).toMillis());
            timer.schedule(() -> close(session, UNAUTHORIZED), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void viewing(UUID userId, String chatId) {
        try {
            presenceTracker.touchChat(userId, UUID.fromString(chatId));
        } catch (IllegalArgumentException ignored) {
            // неверный chatId — просто не учитываем
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
        WebSocketSession session = safeSessions.remove(rawSession.getId());

        if (session == null) {
            session = rawSession;
        }

        UUID userId = userOf(session);

        if (userId != null) {
            registry.remove(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket transport error, session {}: {}", session.getId(), exception.toString());
    }

    /**
     * Раз в 25 секунд — ping всем соединениям: прокси (Render) закрывает молчащие соединения,
     * а оборванные так обнаруживаются и убираются.
     */
    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        for (WebSocketSession session : safeSessions.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage());
                }
            } catch (Exception ex) {
                close(session, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    private static UUID userOf(WebSocketSession session) {
        return (UUID) session.getAttributes().get(ATTR_USER);
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            log.debug("WebSocket send failed, session {}: {}", session.getId(), ex.toString());
        }
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
            // соединение уже закрыто
        }
    }

    @PreDestroy
    void shutdown() {
        timer.shutdownNow();
    }
}
