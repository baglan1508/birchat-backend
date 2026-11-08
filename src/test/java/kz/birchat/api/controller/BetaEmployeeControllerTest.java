
package kz.birchat.api.controller;

import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetaEmployeeControllerTest {

    @Mock
    private EmployeeService employees;

    @Mock
    private BetaIdentityService identity;

    @InjectMocks
    private BetaEmployeeController controller;

    private final UUID companyId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void directorCannotAssignAnotherDirector() {
        Jwt jwt = jwt();
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "+77000000003",
                "Второй Директор",
                "DIRECTOR",
                "Директор"
        );

        when(identity.currentUserId(jwt, null))
                .thenReturn(actorId);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> controller.add(companyId, jwt, null, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());

        verify(identity).requireDirector(companyId, actorId);
        verifyNoInteractions(employees);
    }

    @Test
    void directorCanAddRegularEmployee() {
        Jwt jwt = jwt();
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "+77000000004",
                "Обычный сотрудник",
                "EMPLOYEE",
                "Сотрудник"
        );

        when(identity.currentUserId(jwt, null))
                .thenReturn(actorId);

        assertDoesNotThrow(
                () -> controller.add(companyId, jwt, null, request)
        );

        verify(identity).requireDirector(companyId, actorId);
        verify(employees).addEmployee(companyId, actorId, request);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(actorId.toString())
                .claim("typ", "access")
                .build();
    }
}
