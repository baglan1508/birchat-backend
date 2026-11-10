package kz.birchat.api.service;

import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private CompanyFileRepository companyFileRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @InjectMocks
    private SearchService searchService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void outsiderCannotSearchCompanyData() {
        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> searchService.search(companyId, userId, "test", 20));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());
        verifyNoInteractions(chatMessageRepository, companyFileRepository);
    }

    @Test
    void employeeSearchIsLimitedToTheirChats() {
        mockMember("EMPLOYEE");
        when(chatMessageRepository.searchAccessibleMessages(
                eq(companyId), eq(userId), eq(false), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());
        when(companyFileRepository.searchAccessibleFiles(
                eq(companyId), eq(userId), eq(false), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(searchService.search(companyId, userId, "test", 20).isEmpty());
    }

    @Test
    void directorSearchesAllChats() {
        mockMember("DIRECTOR");
        when(chatMessageRepository.searchAccessibleMessages(
                eq(companyId), eq(userId), eq(true), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());
        when(companyFileRepository.searchAccessibleFiles(
                eq(companyId), eq(userId), eq(true), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(searchService.search(companyId, userId, "test", 20).isEmpty());
    }

    @Test
    void rejectsTooLongQuery() {
        ApiException exception = assertThrows(ApiException.class,
                () -> searchService.search(companyId, userId, "x".repeat(201), 20));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());
        verifyNoInteractions(companyMemberRepository, chatMessageRepository, companyFileRepository);
    }

    private void mockMember(String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setRole(role);

        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(Optional.of(member));
    }
}
