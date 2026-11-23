package kz.birchat.api.service;

import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
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

    @Mock
    private ChatMemberRepository chatMemberRepository;

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
        // ответ не раскрывает, что номер уже зарегистрирован
        assertNull(response.userId());
        assertEquals("Тестовый Сотрудник", response.fullName());
        assertEquals("+77000000004", response.phone());
        assertNull(response.avatarUrl());
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
        assertEquals("Тестовый Сотрудник", savedMember.getInvitedName());
        assertTrue(savedMember.isPending());
    }

    @Test
    void registeredNumberDoesNotRevealProfile() {
        mockActor("DIRECTOR");
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        RoleEntity employeeRole = createRole("EMPLOYEE", "Сотрудник");

        UserEntity registered = new UserEntity();
        registered.setId(UUID.randomUUID());
        registered.setPhone("+77000000005");
        registered.setFullName("Айгерим Сатпаева");
        registered.setDisplayName("Айгерим");
        registered.setInitials("АС");
        registered.setAvatarUrl("avatars/a.jpg");

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(roleRepository.findByCode("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findByPhone("+77000000005")).thenReturn(Optional.of(registered));
        when(companyMemberRepository.save(any(CompanyMemberEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeResponse response = employeeService.addEmployee(companyId, actorId,
                new CreateEmployeeRequest("+77000000005", "Бухгалтер Света", "EMPLOYEE", null));

        assertNull(response.userId());
        assertEquals("Бухгалтер Света", response.fullName());
        assertEquals("Бухгалтер", response.displayName());
        assertEquals("БС", response.initials());
        assertNull(response.avatarUrl());
    }

    @Test
    void pendingMemberIsListedUnderInvitedName() {
        CompanyMemberEntity actor = mockActor("DIRECTOR");
        UserEntity actorUser = new UserEntity();
        actorUser.setId(actorId);
        actorUser.setFullName("Директор");
        actor.setUser(actorUser);
        actor.setAcceptedAt(java.time.LocalDateTime.now());

        UserEntity registered = new UserEntity();
        registered.setId(UUID.randomUUID());
        registered.setFullName("Айгерим Сатпаева");
        registered.setDisplayName("Айгерим");
        registered.setInitials("АС");
        registered.setAvatarUrl("avatars/a.jpg");

        CompanyMemberEntity pending = new CompanyMemberEntity();
        pending.setId(UUID.randomUUID());
        pending.setUser(registered);
        pending.setRole(createRole("EMPLOYEE", "Сотрудник"));
        pending.setStatus("ACTIVE");
        pending.setInvitedName("Бухгалтер Света");

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(new CompanyEntity()));
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(java.util.List.of(actor, pending));

        var list = employeeService.getEmployees(companyId, actorId);

        assertEquals("Директор", list.get(0).fullName());
        assertEquals("Бухгалтер Света", list.get(1).fullName());
        assertNull(list.get(1).avatarUrl());
        assertEquals(registered.getId(), list.get(1).userId());

        pending.setAcceptedAt(java.time.LocalDateTime.now());
        assertEquals("Айгерим Сатпаева", employeeService.getEmployees(companyId, actorId).get(1).fullName());
    }

    @Test
    void removedEmployeeIsAddedBackWithSameRecord() {
        mockActor("DIRECTOR");
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        RoleEntity employeeRole = createRole("EMPLOYEE", "Сотрудник");

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFullName("Нурлан");

        CompanyMemberEntity removed = new CompanyMemberEntity();
        removed.setId(UUID.randomUUID());
        removed.setUser(user);
        removed.setStatus("REMOVED");
        removed.setAcceptedAt(java.time.LocalDateTime.now());

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(roleRepository.findByCode("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findByPhone("+77000000006")).thenReturn(Optional.of(user));
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, user.getId()))
                .thenReturn(Optional.of(removed));
        when(companyMemberRepository.save(any(CompanyMemberEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeResponse response = employeeService.addEmployee(companyId, actorId,
                new CreateEmployeeRequest("+77000000006", "Нурлан", "EMPLOYEE", "Склад"));

        assertEquals(removed.getId(), response.memberId());
        assertEquals("ACTIVE", removed.getStatus());
        assertEquals("Склад", removed.getPosition());
        assertTrue(removed.isPending());
    }

    @Test
    void activeMemberCannotBeAddedTwice() {
        mockActor("DIRECTOR");
        CompanyEntity company = new CompanyEntity();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFullName("Нурлан");
        CompanyMemberEntity active = new CompanyMemberEntity();
        active.setStatus("ACTIVE");

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(roleRepository.findByCode("EMPLOYEE")).thenReturn(Optional.of(createRole("EMPLOYEE", "Сотрудник")));
        when(userRepository.findByPhone("+77000000007")).thenReturn(Optional.of(user));
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, user.getId()))
                .thenReturn(Optional.of(active));

        ApiException exception = assertThrows(ApiException.class, () -> employeeService.addEmployee(
                companyId, actorId, new CreateEmployeeRequest("+77000000007", "Нурлан", "EMPLOYEE", null)));

        assertEquals(ApiErrorCode.ALREADY_MEMBER, exception.getCode());
    }

    @Test
    void directorRemovesEmployeeFromCompanyAndGroups() {
        mockActor("DIRECTOR");
        UUID employeeId = UUID.randomUUID();
        CompanyMemberEntity employee = new CompanyMemberEntity();
        employee.setRole(createRole("EMPLOYEE", "Сотрудник"));
        employee.setStatus("ACTIVE");
        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, employeeId, "ACTIVE"))
                .thenReturn(Optional.of(employee));

        employeeService.removeEmployee(companyId, actorId, employeeId);

        assertEquals("REMOVED", employee.getStatus());
        verify(companyMemberRepository).save(employee);
        verify(chatMemberRepository).deleteFromCompanyGroups(companyId, employeeId);
    }

    @Test
    void directorCannotRemoveHimself() {
        mockActor("DIRECTOR");

        ApiException exception = assertThrows(ApiException.class,
                () -> employeeService.removeEmployee(companyId, actorId, actorId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void employeeCannotRemoveColleague() {
        mockActor("EMPLOYEE");

        ApiException exception = assertThrows(ApiException.class,
                () -> employeeService.removeEmployee(companyId, actorId, UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void removingUnknownEmployeeIsNotFound() {
        mockActor("DIRECTOR");
        UUID stranger = UUID.randomUUID();

        ApiException exception = assertThrows(ApiException.class,
                () -> employeeService.removeEmployee(companyId, actorId, stranger));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ApiErrorCode.USER_NOT_FOUND, exception.getCode());
    }

    private CompanyMemberEntity mockActor(String roleCode) {
        RoleEntity actorRole = createRole(roleCode, roleCode);

        CompanyMemberEntity actorMember = new CompanyMemberEntity();
        actorMember.setRole(actorRole);
        actorMember.setStatus("ACTIVE");

        when(companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(
                        companyId, actorId, "ACTIVE"
                ))
                .thenReturn(Optional.of(actorMember));

        return actorMember;
    }

    private RoleEntity createRole(String code, String name) {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(name);
        return role;
    }
}
