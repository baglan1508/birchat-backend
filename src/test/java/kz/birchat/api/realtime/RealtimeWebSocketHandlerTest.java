package kz.birchat.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.push.ChatPresenceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private WebSocketSession session;

    private final RealtimeSessionRegistry registry = new RealtimeSessionRegistry();
    private final ChatPresenceTracker presenceTracker = new ChatPresenceTracker();
    private RealtimeWebSocketHandler handler;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new RealtimeWebSocketHandler(jwtDecoder, currentUserService, registry, presenceTracker, new ObjectMapper());
        lenient().when(session.getId()).thenReturn("s1");
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
    }

    @Test
    void validTokenRegistersConnection() throws Exception {
        authenticate();

        assertEquals(1, registry.connectionCount());
        assertTrue(sent().get(0).contains("\"type\":\"auth.ok\""));
        assertTrue(sent().get(0).contains(userId.toString()));
    }

    @Test
    void badTokenClosesConnectionWith4401() throws Exception {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("expired"));

        handler.handleMessage(session, new TextMessage("{\"type\":\"auth\",\"accessToken\":\"bad\"}"));

        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        assertTrue(registry.isEmpty());
    }

    @Test
    void anythingBeforeAuthClosesConnection() throws Exception {
        handler.handleMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
    }

    @Test
    void pingGetsPongAndViewingMarksChatOpen() throws Exception {
        authenticate();
        UUID chatId = UUID.randomUUID();

        handler.handleMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        handler.handleMessage(session, new TextMessage("{\"type\":\"viewing\",\"chatId\":\"" + chatId + "\"}"));

        assertTrue(sent().contains("{\"type\":\"pong\"}"));
        assertTrue(presenceTracker.isInChat(userId, chatId, null));
    }

    @Test
    void closedConnectionIsForgotten() throws Exception {
        authenticate();

        handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL);

        assertTrue(registry.isEmpty());
    }

    private void authenticate() throws Exception {
        Jwt jwt = Jwt.withTokenValue("good")
                .header("alg", "HS256")
                .subject(userId.toString())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(jwtDecoder.decode("good")).thenReturn(jwt);
        when(currentUserService.currentUserId(jwt)).thenReturn(userId);

        handler.handleMessage(session, new TextMessage("{\"type\":\"auth\",\"accessToken\":\"good\"}"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> sent() throws Exception {
        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(message -> String.valueOf(message.getPayload())).toList();
    }
}
