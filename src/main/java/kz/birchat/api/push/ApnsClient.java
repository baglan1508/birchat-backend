package kz.birchat.api.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Клиент APNs: HTTP/2, авторизация токеном (JWT ES256, подписанный ключом .p8).
 * Токен авторизации Apple разрешает обновлять не чаще раза в 20 минут и принимает не старше часа —
 * держим его 40 минут.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApnsClient implements PushSender {

    static final String PRODUCTION_HOST = "https://api.push.apple.com";
    static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";
    private static final Duration AUTH_TOKEN_TTL = Duration.ofMinutes(40);

    private final ApnsProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String authToken;
    private Instant authTokenIssuedAt;

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public Result send(String deviceToken, String environment, String payloadJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host(environment) + "/3/device/" + deviceToken))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "bearer " + authToken(Instant.now()))
                    .header("apns-topic", properties.getBundleId().trim())
                    .header("apns-push-type", "alert")
                    .header("apns-priority", "10")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return toResult(response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception ex) {
            log.warn("APNs request failed: {}", ex.toString());
            return Result.FAILED;
        }
    }

    static String host(String environment) {
        return "sandbox".equals(environment) ? SANDBOX_HOST : PRODUCTION_HOST;
    }

    /** 410 Unregistered и 400 BadDeviceToken — токен устройства больше не годится. */
    static Result toResult(int status, String body) {
        if (status == 200) {
            return Result.SENT;
        }

        String reason = body == null ? "" : body;

        if (status == 410 || (status == 400 && reason.contains("BadDeviceToken"))) {
            return Result.INVALID_TOKEN;
        }

        // 403 — неверный ключ/Key ID/Team ID, 400 TopicDisallowed — неверный Bundle ID и т.п.
        log.warn("APNs rejected a notification. status={} body={}", status,
                reason.length() > 200 ? reason.substring(0, 200) : reason);

        return Result.FAILED;
    }

    private synchronized String authToken(Instant now) throws Exception {
        if (authToken == null || authTokenIssuedAt.plus(AUTH_TOKEN_TTL).isBefore(now)) {
            authToken = buildAuthToken(
                    properties.getKeyId().trim(),
                    properties.getTeamId().trim(),
                    parsePrivateKey(properties.getPrivateKey()),
                    now
            );
            authTokenIssuedAt = now;
        }

        return authToken;
    }

    static String buildAuthToken(String keyId, String teamId, ECPrivateKey key, Instant now) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
                new JWTClaimsSet.Builder().issuer(teamId).issueTime(Date.from(now)).build()
        );
        jwt.sign(new ECDSASigner(key));

        return jwt.serialize();
    }

    /**
     * Ключ из .p8: PEM с заголовками или без, переносы строк могут прийти как «\n» текстом
     * (так бывает при вставке в переменную окружения).
     */
    static ECPrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] der = Base64.getDecoder().decode(base64);

        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
