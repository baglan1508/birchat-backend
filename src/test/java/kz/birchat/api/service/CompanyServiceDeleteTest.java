package kz.birchat.api.service;

import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.RoleRepository;
import kz.birchat.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceDeleteTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private CompanyMemberRepository companyMemberRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatReadStateRepository chatReadStateRepository;

    @InjectMocks
    private CompanyService companyService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void directorDeletesCompanyAndEveryoneLosesAccess() {
        CompanyEntity company = new CompanyEntity();
        company.setStatus("ACTIVE");
        mockMember("DIRECTOR", company);

        companyService.deleteCompany(companyId, userId);

        assertEquals("DELETED", company.getStatus());
        verify(companyRepository).save(company);
        verify(companyMemberRepository).changeActiveStatus(companyId, "COMPANY_DELETED");
    }

    @Test
    void employeeCannotDeleteCompany() {
        CompanyEntity company = new CompanyEntity();
        company.setStatus("ACTIVE");
        mockMember("EMPLOYEE", company);

        ApiException exception = assertThrows(ApiException.class,
                () -> companyService.deleteCompany(companyId, userId));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ACTIVE", company.getStatus());
        verify(companyMemberRepository, never()).changeActiveStatus(any(), any());
    }

    @Test
    void outsiderCannotDeleteCompany() {
        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> companyService.deleteCompany(companyId, userId));

        verifyNoInteractions(companyRepository);
    }

    private void mockMember(String roleCode, CompanyEntity company) {
        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setRole(role);
        member.setCompany(company);
        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(Optional.of(member));
    }
}
