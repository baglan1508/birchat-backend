package kz.birchat.api.service;

import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the authenticated beta user. Client-supplied identity fields are
 * accepted for compatibility only, never trusted as the source of identity.
 */
@Service
@Profile("beta")
@RequiredArgsConstructor
public class BetaIdentityService {
    private final UserRepository users;
    private final CompanyMemberRepository memberships;

    public UUID currentUserId(Jwt jwt) {
        if (jwt == null || !"access".equals(jwt.getClaimAsString("typ"))) {
            throw unauthenticated();
        }
        final UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw unauthenticated();
        }
        boolean active = users.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .isPresent();
        if (!active) throw unauthenticated();
        return userId;
    }

    public UUID currentUserId(Jwt jwt, UUID suppliedId) {
        UUID authenticated = currentUserId(jwt);
        if (suppliedId != null && !authenticated.equals(suppliedId)) {
            throw ApiException.forbidden(ApiErrorCode.FORBIDDEN,
                    "Идентификатор пользователя не совпадает с JWT");
        }
        return authenticated;
    }

    public void requireDirector(UUID companyId, UUID userId) {
        CompanyMemberEntity member = memberships
                .findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в компании"));
        if (!"DIRECTOR".equals(member.getRole().getCode())) {
            throw ApiException.forbidden(ApiErrorCode.FORBIDDEN,
                    "Операция доступна только директору компании");
        }
    }

    private ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_ACCESS_TOKEN,
                "Токен не относится к активному пользователю");
    }
}
