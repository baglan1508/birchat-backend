package kz.birchat.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@Profile("beta")
public class BetaJwtConfig {
    @Bean
    SecretKey betaJwtKey(@Value("${auth.jwt.secret-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length < 32) throw new IllegalStateException("BETA_JWT_SECRET_BASE64 must contain at least 32 random bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey betaJwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(betaJwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey betaJwtKey,
                          @Value("${auth.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(betaJwtKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
