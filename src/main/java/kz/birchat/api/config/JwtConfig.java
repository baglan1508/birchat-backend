package kz.birchat.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Ключ и кодеки для access-токенов (HS256).
 *
 * auth.jwt.secret — случайная строка не короче 32 байт (переменная AUTH_JWT_SECRET).
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(@Value("${auth.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "auth.jwt.secret не задан (переменная окружения AUTH_JWT_SECRET)"
            );
        }

        byte[] key = secret.getBytes(StandardCharsets.UTF_8);

        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "auth.jwt.secret должен быть не короче " + MIN_SECRET_BYTES + " байт"
            );
        }

        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            @Value("${auth.jwt.issuer:birchat}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

        return decoder;
    }
}
