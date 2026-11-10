package kz.birchat.api.auth;

import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Определяет текущего пользователя по access-токену и проверяет роль директора.
 * Идентификатор пользователя берётся только из JWT, никогда из параметров запроса.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    public static final String ROLE_DIRECTOR = "DIRECTOR";

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

        if (!active) {
            throw unauthenticated();
        }

        return userId;
    }

    public CompanyMemberEntity requireActiveMember(UUID companyId, UUID userId) {
        return memberships
                .findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в компании"
                ));
    }

    public CompanyMemberEntity requireDirector(UUID companyId, UUID userId) {
        CompanyMemberEntity member = requireActiveMember(companyId, userId);

        if (!isDirector(member)) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Операция доступна только директору компании"
            );
        }

        return member;
    }

    public static boolean isDirector(CompanyMemberEntity member) {
        return member != null
                && member.getRole() != null
                && ROLE_DIRECTOR.equals(member.getRole().getCode());
    }

    private ApiException unauthenticated() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.INVALID_ACCESS_TOKEN,
                "Токен не относится к активному пользователю"
        );
    }
}
