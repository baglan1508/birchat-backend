package kz.birchat.api.service;

import kz.birchat.api.dto.TokenPairResponse;
import kz.birchat.api.entity.RefreshTokenEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.RefreshTokenRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Profile("beta")
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();
    @Value("${auth.jwt.issuer}") private String issuer;
    @Value("${auth.jwt.access-ttl-seconds:900}") private long accessTtl;
    @Value("${auth.jwt.refresh-ttl-days:30}") private long refreshDays;

    @Transactional
    public TokenPairResponse issue(UserEntity user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) throw invalid();
        return issueInternal(user);
    }

    private TokenPairResponse issueInternal(UserEntity user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).issuedAt(now).expiresAt(now.plusSeconds(accessTtl))
                .subject(user.getId().toString()).claim("typ", "access")
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setTokenHash(hash(refresh));
        entity.setCreatedAt(TimeUtils.utcNow());
        entity.setExpiresAt(TimeUtils.utcNow().plusDays(refreshDays));
        tokens.save(entity);
        return new TokenPairResponse(access, refresh, "Bearer", accessTtl);
    }

    @Transactional
    public TokenPairResponse refresh(String presented) {
        RefreshTokenEntity old = tokens.findByTokenHash(hash(presented)).orElseThrow(this::invalid);
        LocalDateTime now = TimeUtils.utcNow();
        if (old.getRevokedAt() != null || !old.getExpiresAt().isAfter(now)) throw invalid();
        UserEntity user = users.findById(old.getUserId()).orElseThrow(this::invalid);
        if (!Boolean.TRUE.equals(user.getIsActive())) throw invalid();
        old.setRevokedAt(now);
        tokens.saveAndFlush(old);
        return issueInternal(user);
    }

    @Transactional
    public void logout(String presented) {
        tokens.findByTokenHash(hash(presented)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(TimeUtils.utcNow()); tokens.save(token);
            }
        });
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_REFRESH_TOKEN,
                "Недействительный refresh token");
    }
}
