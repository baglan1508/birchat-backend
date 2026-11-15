package kz.birchat.api.push;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ApnsClientTest {

    @Test
    void authTokenIsEs256SignedWithKeyIdAndTeamId() throws Exception {
        KeyPair keyPair = p256();
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        String token = ApnsClient.buildAuthToken("ABC123DEFG", "S59B8XNQ5Z", (ECPrivateKey) keyPair.getPrivate(), now);
        SignedJWT jwt = SignedJWT.parse(token);

        assertEquals("ES256", jwt.getHeader().getAlgorithm().getName());
        assertEquals("ABC123DEFG", jwt.getHeader().getKeyID());
        assertEquals("S59B8XNQ5Z", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(now, jwt.getJWTClaimsSet().getIssueTime().toInstant());
        assertTrue(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())));
    }

    @Test
    void p8KeyIsReadWithHeadersAndEscapedNewlines() throws Exception {
        KeyPair keyPair = p256();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";

        assertEquals(keyPair.getPrivate(), ApnsClient.parsePrivateKey(pem));
        assertEquals(keyPair.getPrivate(), ApnsClient.parsePrivateKey(pem.replace("\n", "\\n")));
        assertEquals(keyPair.getPrivate(), ApnsClient.parsePrivateKey(base64.replace("\n", "")));
    }

    @Test
    void unregisteredAndBadTokensMustBeRemoved() {
        assertEquals(PushSender.Result.SENT, ApnsClient.toResult(200, ""));
        assertEquals(PushSender.Result.INVALID_TOKEN, ApnsClient.toResult(410, "{\"reason\":\"Unregistered\"}"));
        assertEquals(PushSender.Result.INVALID_TOKEN, ApnsClient.toResult(400, "{\"reason\":\"BadDeviceToken\"}"));
        assertEquals(PushSender.Result.FAILED, ApnsClient.toResult(400, "{\"reason\":\"TopicDisallowed\"}"));
        assertEquals(PushSender.Result.FAILED, ApnsClient.toResult(403, "{\"reason\":\"InvalidProviderToken\"}"));
    }

    @Test
    void sandboxTokensGoToSandboxHost() {
        assertEquals("https://api.sandbox.push.apple.com", ApnsClient.host("sandbox"));
        assertEquals("https://api.push.apple.com", ApnsClient.host("production"));
    }

    @Test
    void pushesAreOffUntilKeyIsSet() {
        ApnsProperties properties = new ApnsProperties();
        assertFalse(properties.isConfigured());

        properties.setKeyId("ABC123DEFG");
        properties.setPrivateKey("-----BEGIN PRIVATE KEY-----…");
        assertTrue(properties.isConfigured());
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
