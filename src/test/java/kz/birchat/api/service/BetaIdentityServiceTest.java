package kz.birchat.api.service;

import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetaIdentityServiceTest {
    private final UUID currentId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Mock UserRepository users;
    @Mock CompanyMemberRepository memberships;
    @InjectMocks BetaIdentityService identity;

    private Jwt jwt;

    @BeforeEach
    void init() {
        jwt = Jwt.withTokenValue("unit-test")
                .header("alg", "HS256")
                .subject(currentId.toString())
                .claim("typ", "access")
                .build();
    }

    @Test
    void usesJwtSubjectWhenLegacyUserIdIsMissing() {
        activeUser();
        assertEquals(currentId, identity.currentUserId(jwt, null));
    }

    @Test
    void rejectsForeignLegacyUserIdEvenWithValidJwt() {
        activeUser();
        ApiException error = assertThrows(ApiException.class,
                () -> identity.currentUserId(jwt, UUID.randomUUID()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void rejectsInactiveAccount() {
        UserEntity user = new UserEntity();
        user.setId(currentId);
        user.setIsActive(false);
        when(users.findById(currentId)).thenReturn(Optional.of(user));
        ApiException error = assertThrows(ApiException.class,
                () -> identity.currentUserId(jwt));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    @Test
    void rejectsJwtThatIsNotAccessToken() {
        Jwt invalidType = Jwt.withTokenValue("unit-test")
                .header("alg", "HS256")
                .subject(currentId.toString())
                .claim("typ", "refresh")
                .build();
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ApiException.class, () -> identity.currentUserId(invalidType)).getStatus());
        verifyNoInteractions(users);
    }

    @Test
    void adminCannotAddEmployeesOrViewDirectorSummaryInBeta() {
        CompanyMemberEntity member = new CompanyMemberEntity();
        RoleEntity role = new RoleEntity();
        role.setCode("ADMIN");
        member.setRole(role);
        when(memberships.findByCompanyIdAndUserIdAndStatus(companyId, currentId, "ACTIVE"))
                .thenReturn(Optional.of(member));
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ApiException.class,
                        () -> identity.requireDirector(companyId, currentId)).getStatus());
    }

    @Test
    void directorIsAllowed() {
        CompanyMemberEntity member = new CompanyMemberEntity();
        RoleEntity role = new RoleEntity();
        role.setCode("DIRECTOR");
        member.setRole(role);
        when(memberships.findByCompanyIdAndUserIdAndStatus(companyId, currentId, "ACTIVE"))
                .thenReturn(Optional.of(member));
        assertDoesNotThrow(() -> identity.requireDirector(companyId, currentId));
    }

    private void activeUser() {
        UserEntity user = new UserEntity();
        user.setId(currentId);
        user.setIsActive(true);
        when(users.findById(currentId)).thenReturn(Optional.of(user));
    }
}
