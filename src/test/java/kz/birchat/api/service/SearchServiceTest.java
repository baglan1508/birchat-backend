
package kz.birchat.api.service;

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
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void outsiderCannotSearchCompanyData() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(companyMemberRepository
                .existsByCompanyIdAndUserIdAndStatus(
                        companyId, userId, "ACTIVE"))
                .thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> searchService.search(
                        companyId, userId, "test", 20)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(chatMessageRepository);
        verifyNoInteractions(companyFileRepository);
    }

    @Test
    void betaSearchUsesOnlyGeneralChatMessages() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(companyMemberRepository
                .existsByCompanyIdAndUserIdAndStatus(
                        companyId, userId, "ACTIVE"))
                .thenReturn(true);

        when(chatMessageRepository.searchGeneralMessages(
                eq(companyId), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        when(companyFileRepository.searchFilesLiteral(
                eq(companyId), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        var results = searchService.searchBeta(
                companyId, userId, "test", 20);

        assertTrue(results.isEmpty());

        verify(chatMessageRepository).searchGeneralMessages(
                eq(companyId), eq("test"), any(Pageable.class));

        verify(chatMessageRepository, never())
                .searchMessages(any(), any(), any());
    }

    @Test
    void betaRejectsTooLongSearchQuery() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> searchService.searchBeta(
                        companyId, userId, "x".repeat(201), 20)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());

        verifyNoInteractions(companyMemberRepository);
        verifyNoInteractions(chatMessageRepository);
        verifyNoInteractions(companyFileRepository);
    }
}
