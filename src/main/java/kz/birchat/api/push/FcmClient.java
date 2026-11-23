package kz.birchat.api.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Клиент FCM HTTP v1. Авторизация: JWT, подписанный ключом сервисного аккаунта (RS256),
 * меняется на access token Google (живёт час) — держим его 50 минут.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmClient {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(50);

    private final FcmProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ServiceAccount account;
    private String accessToken;
    private Instant accessTokenIssuedAt;

    record ServiceAccount(String projectId, String clientEmail, RSAPrivateKey privateKey, String tokenUri) {
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** message — объект «message» FCM без поля token: его подставляем здесь. */
    public PushSender.Result send(String deviceToken, JsonNode message) {
        try {
            ServiceAccount sa = account();
            com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode withToken = message.deepCopy();
            withToken.put("token", deviceToken);
            body.set("message", withToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + sa.projectId() + "/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "Bearer " + accessToken(Instant.now()))
                    .header("content-type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                // Токен отозван раньше срока — в следующий раз возьмём новый.
                resetAccessToken();
            }

            return toResult(response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PushSender.Result.FAILED;
        } catch (Exception ex) {
            log.warn("FCM request failed: {}", ex.toString());
            return PushSender.Result.FAILED;
        }
    }

    /**
     * UNREGISTERED (404) — приложение удалено; INVALID_ARGUMENT про токен — токен не FCM.
     * Такие устройства удаляем, как 410 от APNs.
     */
    static PushSender.Result toResult(int status, String body) {
        if (status == 200) {
            return PushSender.Result.SENT;
        }

        String reason = body == null ? "" : body;

        if (status == 404 || reason.contains("UNREGISTERED")
                || (status == 400 && reason.contains("INVALID_ARGUMENT") && reason.toLowerCase().contains("registration token"))) {
            return PushSender.Result.INVALID_TOKEN;
        }

        log.warn("FCM rejected a notification. status={} body={}", status,
                reason.length() > 300 ? reason.substring(0, 300) : reason);

        return PushSender.Result.FAILED;
    }

    private synchronized ServiceAccount account() throws Exception {
        if (account == null) {
            account = parseServiceAccount(objectMapper, properties.getServiceAccountJson());
        }

        return account;
    }

    private synchronized void resetAccessToken() {
        accessToken = null;
    }

    private synchronized String accessToken(Instant now) throws Exception {
        if (accessToken == null || accessTokenIssuedAt.plus(ACCESS_TOKEN_TTL).isBefore(now)) {
            ServiceAccount sa = account();
            String assertion = buildAssertion(sa, now);

            String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                    + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(sa.tokenUri()))
                            .timeout(Duration.ofSeconds(15))
                            .header("content-type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Google OAuth: " + response.statusCode() + " "
                        + (response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length()))));
            }

            accessToken = objectMapper.readTree(response.body()).path("access_token").asText();
            accessTokenIssuedAt = now;
        }

        return accessToken;
    }

    static String buildAssertion(ServiceAccount sa, Instant now) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                new JWTClaimsSet.Builder()
                        .issuer(sa.clientEmail())
                        .audience(sa.tokenUri())
                        .claim("scope", SCOPE)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                        .build()
        );
        jwt.sign(new RSASSASigner(sa.privateKey()));

        return jwt.serialize();
    }

    static ServiceAccount parseServiceAccount(ObjectMapper objectMapper, String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        String projectId = node.path("project_id").asText("");
        String clientEmail = node.path("client_email").asText("");
        String privateKeyPem = node.path("private_key").asText("");
        String tokenUri = node.path("token_uri").asText(DEFAULT_TOKEN_URI);

        if (projectId.isBlank() || clientEmail.isBlank() || privateKeyPem.isBlank()) {
            throw new IllegalStateException("FCM_SERVICE_ACCOUNT_JSON: нет project_id, client_email или private_key");
        }

        String base64 = privateKeyPem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));

        return new ServiceAccount(projectId, clientEmail, key, tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri);
    }
}
