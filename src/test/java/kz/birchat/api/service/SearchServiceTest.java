package kz.birchat.api.service;

import kz.birchat.api.dto.SearchResultResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(chatMessageRepository.searchAccessibleMessageIds(
                companyId, userId, false, "test", 20))
                .thenReturn(List.of());
        when(companyFileRepository.searchAccessibleFiles(
                eq(companyId), eq(userId), eq(false), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(searchService.search(companyId, userId, "test", 20).isEmpty());
    }

    @Test
    void directorSearchesAllChats() {
        mockMember("DIRECTOR");
        when(chatMessageRepository.searchAccessibleMessageIds(
                companyId, userId, true, "test", 20))
                .thenReturn(List.of());
        when(companyFileRepository.searchAccessibleFiles(
                eq(companyId), eq(userId), eq(true), eq("test"), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(searchService.search(companyId, userId, "test", 20).isEmpty());
    }

    @Test
    void foundMessagesKeepIndexOrder() {
        mockMember("EMPLOYEE");
        ChatMessageEntity newer = message("Подписали договора", LocalDateTime.of(2026, 11, 11, 10, 0));
        ChatMessageEntity older = message("Договор аренды", LocalDateTime.of(2026, 11, 10, 10, 0));

        when(chatMessageRepository.searchAccessibleMessageIds(companyId, userId, false, "договоры", 20))
                .thenReturn(List.of(newer.getId().toString(), older.getId().toString()));
        when(chatMessageRepository.findAllWithUserAndChatByIdIn(List.of(newer.getId(), older.getId())))
                .thenReturn(List.of(older, newer));

        List<SearchResultResponse> results = searchService.search(companyId, userId, "договоры", 20);

        assertEquals(List.of(newer.getId(), older.getId()),
                results.stream().map(SearchResultResponse::id).toList());
        assertEquals("MESSAGE", results.get(0).type());
        assertEquals("Сообщение от Айгерим", results.get(0).title());
    }

    @Test
    void queryWithoutLettersOrDigitsDoesNotHitMessageIndex() {
        mockMember("EMPLOYEE");

        assertTrue(searchService.search(companyId, userId, "!!", 20).isEmpty());

        verify(chatMessageRepository, never())
                .searchAccessibleMessageIds(any(), any(), anyBoolean(), anyString(), anyInt());
        verify(companyFileRepository)
                .searchAccessibleFiles(eq(companyId), eq(userId), eq(false), eq("!!"), any(Pageable.class));
    }

    @Test
    void limitIsCappedBeforeIndexSearch() {
        mockMember("DIRECTOR");

        searchService.search(companyId, userId, "договор", 500);

        verify(chatMessageRepository).searchAccessibleMessageIds(companyId, userId, true, "договор", 50);
    }

    @Test
    void lettersOrDigitsDetection() {
        assertTrue(SearchService.hasLettersOrDigits("договор"));
        assertTrue(SearchService.hasLettersOrDigits("кітап"));
        assertTrue(SearchService.hasLettersOrDigits("№15"));
        assertFalse(SearchService.hasLettersOrDigits("!! -- ??"));
    }

    @Test
    void rejectsTooLongQuery() {
        ApiException exception = assertThrows(ApiException.class,
                () -> searchService.search(companyId, userId, "x".repeat(201), 20));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ApiErrorCode.VALIDATION, exception.getCode());
        verifyNoInteractions(companyMemberRepository, chatMessageRepository, companyFileRepository);
    }

    private ChatMessageEntity message(String content, LocalDateTime createdAt) {
        UserEntity author = new UserEntity();
        author.setDisplayName("Айгерим");

        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setContent(content);
        message.setCreatedAt(createdAt);
        message.setUser(author);
        message.setChat(chat);
        return message;
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
