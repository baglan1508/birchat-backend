package kz.birchat.api.auth;

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
class CurrentUserServiceTest {

    private final UUID currentId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Mock
    UserRepository users;

    @Mock
    CompanyMemberRepository memberships;

    @InjectMocks
    CurrentUserService identity;

    private Jwt jwt;

    @BeforeEach
    void init() {
        jwt = jwt("access");
    }

    @Test
    void usesJwtSubjectAsUserId() {
        activeUser(true);

        assertEquals(currentId, identity.currentUserId(jwt));
    }

    @Test
    void rejectsInactiveAccount() {
        activeUser(false);

        ApiException error = assertThrows(ApiException.class, () -> identity.currentUserId(jwt));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    @Test
    void rejectsJwtThatIsNotAccessToken() {
        ApiException error = assertThrows(ApiException.class,
                () -> identity.currentUserId(jwt("refresh")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verifyNoInteractions(users);
    }

    @Test
    void adminIsNotDirector() {
        member("ADMIN");

        ApiException error = assertThrows(ApiException.class,
                () -> identity.requireDirector(companyId, currentId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void directorIsAllowed() {
        member("DIRECTOR");

        assertDoesNotThrow(() -> identity.requireDirector(companyId, currentId));
    }

    @Test
    void outsiderIsNotMember() {
        when(memberships.findByCompanyIdAndUserIdAndStatus(companyId, currentId, "ACTIVE"))
                .thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> identity.requireActiveMember(companyId, currentId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    private Jwt jwt(String type) {
        return Jwt.withTokenValue("unit-test")
                .header("alg", "HS256")
                .subject(currentId.toString())
                .claim("typ", type)
                .build();
    }

    private void activeUser(boolean active) {
        UserEntity user = new UserEntity();
        user.setId(currentId);
        user.setIsActive(active);
        when(users.findById(currentId)).thenReturn(Optional.of(user));
    }

    private void member(String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setRole(role);

        when(memberships.findByCompanyIdAndUserIdAndStatus(companyId, currentId, "ACTIVE"))
                .thenReturn(Optional.of(member));
    }
}
