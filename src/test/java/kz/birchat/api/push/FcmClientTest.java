package kz.birchat.api.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FcmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serviceAccountIsParsedAndAssertionIsSigned() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = objectMapper.writeValueAsString(Map.of(
                "type", "service_account",
                "project_id", "birchat-test",
                "client_email", "push@birchat-test.iam.gserviceaccount.com",
                "private_key", pem,
                "token_uri", "https://oauth2.googleapis.com/token"
        ));

        FcmClient.ServiceAccount account = FcmClient.parseServiceAccount(objectMapper, json);

        assertEquals("birchat-test", account.projectId());
        assertEquals("push@birchat-test.iam.gserviceaccount.com", account.clientEmail());

        SignedJWT jwt = SignedJWT.parse(FcmClient.buildAssertion(account, Instant.now()));
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())));
        assertEquals("push@birchat-test.iam.gserviceaccount.com", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(FcmClient.SCOPE, jwt.getJWTClaimsSet().getStringClaim("scope"));
        assertEquals("https://oauth2.googleapis.com/token", jwt.getJWTClaimsSet().getAudience().get(0));
    }

    @Test
    void serviceAccountWithoutKeyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> FcmClient.parseServiceAccount(objectMapper, "{\"project_id\":\"x\"}"));
    }

    @Test
    void deadTokensAreRecognised() {
        assertEquals(PushSender.Result.SENT, FcmClient.toResult(200, "{}"));
        assertEquals(PushSender.Result.INVALID_TOKEN, FcmClient.toResult(404,
                "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));
        assertEquals(PushSender.Result.INVALID_TOKEN, FcmClient.toResult(400,
                "{\"error\":{\"message\":\"The registration token is not a valid FCM registration token\",\"status\":\"INVALID_ARGUMENT\"}}"));
        assertEquals(PushSender.Result.FAILED, FcmClient.toResult(400,
                "{\"error\":{\"message\":\"Invalid JSON payload\",\"status\":\"INVALID_ARGUMENT\"}}"));
        assertEquals(PushSender.Result.FAILED, FcmClient.toResult(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));
    }
}
