package kz.birchat.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.config.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Выпуск и проверка access-токенов в формате JWT (HS256).
 * Реализовано на стандартных средствах JDK, без сторонних JWT-библиотек.
 */
@Service
public class JwtService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final long DEFAULT_TTL_SECONDS = 30L * 24 * 60 * 60;
    private static final String DEFAULT_ISSUER = "birchat";

    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        if (properties == null || properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException(
                    "auth.jwt.secret не задан (переменная окружения AUTH_JWT_SECRET)"
            );
        }

        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "auth.jwt.secret должен быть не короче " + MIN_SECRET_BYTES + " байт"
            );
        }

        this.objectMapper = objectMapper;
        this.secret = secretBytes;
        this.ttlSeconds = properties.accessTokenTtlSeconds() != null && properties.accessTokenTtlSeconds() > 0
                ? properties.accessTokenTtlSeconds()
                : DEFAULT_TTL_SECONDS;
        this.issuer = properties.issuer() != null && !properties.issuer().isBlank()
                ? properties.issuer()
                : DEFAULT_ISSUER;
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(UUID userId) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", userId.toString());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String signingInput = encodeJson(header) + "." + encodeJson(payload);
        String token = signingInput + "." + B64_ENCODER.encodeToString(sign(signingInput));

        return new IssuedToken(token, expiresAt);
    }

    /**
     * Проверяет подпись, алгоритм, издателя и срок действия токена.
     *
     * @return id пользователя из claim "sub"
     * @throws InvalidTokenException если токен невалиден или истёк
     */
    public UUID parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Токен отсутствует", false);
        }

        String[] parts = token.split("\\.", -1);

        if (parts.length != 3) {
            throw new InvalidTokenException("Некорректный формат токена", false);
        }

        byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
        byte[] actualSignature = decode(parts[2]);

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new InvalidTokenException("Неверная подпись токена", false);
        }

        JsonNode header = decodeJson(parts[0]);

        if (!"HS256".equals(header.path("alg").asText())) {
            throw new InvalidTokenException("Неподдерживаемый алгоритм токена", false);
        }

        JsonNode payload = decodeJson(parts[1]);

        if (!issuer.equals(payload.path("iss").asText())) {
            throw new InvalidTokenException("Неверный издатель токена", false);
        }

        JsonNode exp = payload.get("exp");

        if (exp == null || !exp.canConvertToLong()) {
            throw new InvalidTokenException("В токене нет срока действия", false);
        }

        if (Instant.now(clock).getEpochSecond() >= exp.asLong()) {
            throw new InvalidTokenException("Срок действия токена истёк", true);
        }

        try {
            return UUID.fromString(payload.path("sub").asText());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Некорректный пользователь в токене", false);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Не удалось подписать токен", ex);
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return B64_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сериализовать токен", ex);
        }
    }

    private JsonNode decodeJson(String part) {
        try {
            JsonNode node = objectMapper.readTree(decode(part));

            if (node == null || !node.isObject()) {
                throw new InvalidTokenException("Некорректное содержимое токена", false);
            }

            return node;
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidTokenException("Некорректное содержимое токена", false);
        }
    }

    private byte[] decode(String part) {
        try {
            return B64_DECODER.decode(part);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Некорректная кодировка токена", false);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public static class InvalidTokenException extends RuntimeException {

        private final boolean expired;

        public InvalidTokenException(String message, boolean expired) {
            super(message);
            this.expired = expired;
        }

        public boolean isExpired() {
            return expired;
        }
    }
}
