
package kz.birchat.api.service;

import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.RoleRepository;
import kz.birchat.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void adminCannotAddEmployee() {
        mockActor("ADMIN");

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "+77000000002",
                "Тестовый сотрудник",
                "EMPLOYEE",
                "Тестировщик"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> employeeService.addEmployee(
                        companyId, actorId, request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.FORBIDDEN, exception.getCode());

        verifyNoInteractions(
                companyRepository,
                roleRepository,
                userRepository
        );

        verify(companyMemberRepository, never())
                .save(any(CompanyMemberEntity.class));
    }

    @Test
    void directorCannotAssignAnotherDirector() {
        mockActor("DIRECTOR");

        CompanyEntity company = new CompanyEntity();

        RoleEntity directorRole = createRole(
                "DIRECTOR", "Директор"
        );

        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(company));

        when(roleRepository.findByCode("DIRECTOR"))
                .thenReturn(Optional.of(directorRole));

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "+77000000003",
                "Второй директор",
                "DIRECTOR",
                "Директор"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> employeeService.addEmployee(
                        companyId, actorId, request
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());

        verifyNoInteractions(userRepository);

        verify(companyMemberRepository, never())
                .save(any(CompanyMemberEntity.class));
    }

    @Test
    void directorCanAddRegularEmployee() {
        mockActor("DIRECTOR");

        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);

        RoleEntity employeeRole = createRole(
                "EMPLOYEE", "Сотрудник"
        );

        UUID newUserId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(newUserId);
        user.setPhone("+77000000004");
        user.setFullName("Тестовый Сотрудник");
        user.setDisplayName("Тестовый");
        user.setInitials("ТС");

        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(company));

        when(roleRepository.findByCode("EMPLOYEE"))
                .thenReturn(Optional.of(employeeRole));

        when(userRepository.findByPhone("+77000000004"))
                .thenReturn(Optional.of(user));

        when(companyMemberRepository.save(
                any(CompanyMemberEntity.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "+77000000004",
                "Тестовый Сотрудник",
                "EMPLOYEE",
                "Тестировщик"
        );

        EmployeeResponse response = employeeService.addEmployee(
                companyId, actorId, request
        );

        assertNotNull(response);
        assertNotNull(response.memberId());
        assertEquals(newUserId, response.userId());
        assertEquals("EMPLOYEE", response.role());
        assertEquals("ACTIVE", response.status());

        ArgumentCaptor<CompanyMemberEntity> captor =
                ArgumentCaptor.forClass(CompanyMemberEntity.class);

        verify(companyMemberRepository).save(captor.capture());

        CompanyMemberEntity savedMember = captor.getValue();

        assertSame(company, savedMember.getCompany());
        assertSame(user, savedMember.getUser());
        assertSame(employeeRole, savedMember.getRole());
        assertEquals("Тестировщик", savedMember.getPosition());
        assertEquals("ACTIVE", savedMember.getStatus());
    }

    private void mockActor(String roleCode) {
        RoleEntity actorRole = createRole(roleCode, roleCode);

        CompanyMemberEntity actorMember = new CompanyMemberEntity();
        actorMember.setRole(actorRole);
        actorMember.setStatus("ACTIVE");

        when(companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(
                        companyId, actorId, "ACTIVE"
                ))
                .thenReturn(Optional.of(actorMember));
    }

    private RoleEntity createRole(String code, String name) {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(name);
        return role;
    }
}
