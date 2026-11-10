package kz.birchat.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService service(String secret, long ttlSeconds, Instant now) {
        return new JwtService(
                new JwtProperties(secret, ttlSeconds, "birchat"),
                objectMapper,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    @Test
    void issuedTokenIsAccepted() {
        UUID userId = UUID.randomUUID();
        JwtService jwt = service(SECRET, 3600, NOW);

        JwtService.IssuedToken issued = jwt.issueAccessToken(userId);

        assertEquals(userId, jwt.parseAccessToken(issued.token()));
        assertEquals(NOW.plusSeconds(3600), issued.expiresAt());
    }

    @Test
    void expiredTokenIsRejectedAsExpired() {
        String token = service(SECRET, 60, NOW).issueAccessToken(UUID.randomUUID()).token();

        JwtService.InvalidTokenException ex = assertThrows(
                JwtService.InvalidTokenException.class,
                () -> service(SECRET, 60, NOW.plusSeconds(61)).parseAccessToken(token)
        );

        assertTrue(ex.isExpired());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET, 3600, NOW).issueAccessToken(UUID.randomUUID()).token();
        JwtService other = service("another-secret-another-secret-another", 3600, NOW);

        JwtService.InvalidTokenException ex = assertThrows(
                JwtService.InvalidTokenException.class,
                () -> other.parseAccessToken(token)
        );

        assertFalse(ex.isExpired());
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service(SECRET, 3600, NOW);
        String[] parts = jwt.issueAccessToken(UUID.randomUUID()).token().split("\\.");

        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"birchat\",\"sub\":\"" + UUID.randomUUID()
                        + "\",\"exp\":9999999999}").getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                JwtService.InvalidTokenException.class,
                () -> jwt.parseAccessToken(parts[0] + "." + forgedPayload + "." + parts[2])
        );
    }

    @Test
    void unsignedAndMalformedTokensAreRejected() {
        JwtService jwt = service(SECRET, 3600, NOW);
        String[] parts = jwt.issueAccessToken(UUID.randomUUID()).token().split("\\.");

        assertThrows(JwtService.InvalidTokenException.class,
                () -> jwt.parseAccessToken(parts[0] + "." + parts[1] + "."));
        assertThrows(JwtService.InvalidTokenException.class,
                () -> jwt.parseAccessToken("mock-access-token-" + UUID.randomUUID()));
        assertThrows(JwtService.InvalidTokenException.class,
                () -> jwt.parseAccessToken("a.b.c"));
        assertThrows(JwtService.InvalidTokenException.class,
                () -> jwt.parseAccessToken(""));
    }

    @Test
    void shortOrMissingSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> service("short", 3600, NOW));
        assertThrows(IllegalStateException.class, () -> service(null, 3600, NOW));
    }
}
