package kz.birchat.api.service;

import kz.birchat.api.dto.CompanyResponse;
import kz.birchat.api.dto.UpdateCompanyRequest;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.exception.ApiErrorCode;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceSettingsTest {

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
    void directorTurnsAiOn() {
        CompanyEntity company = company(false);
        mockMember("DIRECTOR", company);

        CompanyResponse response = companyService.updateCompany(companyId, userId, new UpdateCompanyRequest(true, null));

        assertTrue(company.isAiEnabled());
        assertTrue(response.aiEnabled());
        verify(companyRepository).save(company);
    }

    @Test
    void missingFieldDoesNotChangeAiSetting() {
        CompanyEntity company = company(true);
        mockMember("DIRECTOR", company);

        CompanyResponse response = companyService.updateCompany(companyId, userId, new UpdateCompanyRequest(null, null));

        assertTrue(response.aiEnabled());
    }

    @Test
    void directorLetsEmployeesCreateGroups() {
        CompanyEntity company = company(true);
        mockMember("DIRECTOR", company);

        CompanyResponse response = companyService.updateCompany(companyId, userId, new UpdateCompanyRequest(null, true));

        assertTrue(company.isMembersCanCreateGroups());
        assertTrue(response.membersCanCreateGroups());
        assertTrue(response.aiEnabled());
    }

    @Test
    void employeeCannotChangeAiSetting() {
        CompanyEntity company = company(false);
        mockMember("EMPLOYEE", company);

        ApiException exception = assertThrows(ApiException.class,
                () -> companyService.updateCompany(companyId, userId, new UpdateCompanyRequest(true, null)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.FORBIDDEN, exception.getCode());
        assertFalse(company.isAiEnabled());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void openingCompanyListAcceptsInvitations() {
        when(companyMemberRepository.findByUserIdAndStatus(userId, "ACTIVE")).thenReturn(List.of());

        companyService.getMyCompanies(userId);

        verify(companyMemberRepository).markAccepted(eq(userId), any(LocalDateTime.class));
    }

    @Test
    void newCompanyStartsWithAiOffAndDirectorAccepted() {
        kz.birchat.api.entity.UserEntity owner = new kz.birchat.api.entity.UserEntity();
        owner.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(roleRepository.findByCode("DIRECTOR")).thenReturn(Optional.of(role("DIRECTOR")));
        when(companyRepository.save(any(CompanyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response = companyService.createCompany(userId,
                new kz.birchat.api.dto.CreateCompanyRequest("Nomad Trade", "Торговля", null, null));

        assertFalse(response.aiEnabled());
        org.mockito.ArgumentCaptor<CompanyMemberEntity> member = org.mockito.ArgumentCaptor.forClass(CompanyMemberEntity.class);
        verify(companyMemberRepository).save(member.capture());
        assertFalse(member.getValue().isPending());
    }

    private CompanyEntity company(boolean aiEnabled) {
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        company.setName("Nomad Trade");
        company.setStatus("ACTIVE");
        company.setAiEnabled(aiEnabled);
        return company;
    }

    private RoleEntity role(String code) {
        RoleEntity role = new RoleEntity();
        role.setCode(code);
        role.setName(code);
        return role;
    }

    private void mockMember(String roleCode, CompanyEntity company) {
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setCompany(company);
        member.setRole(role(roleCode));
        member.setStatus("ACTIVE");
        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(Optional.of(member));
    }
}
