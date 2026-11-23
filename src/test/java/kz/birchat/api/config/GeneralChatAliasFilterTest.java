package kz.birchat.api.config;

import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralChatAliasFilterTest {

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private GeneralChatAliasFilter filter;

    private final UUID companyId = UUID.randomUUID();
    private final UUID generalChatId = UUID.randomUUID();

    @Test
    void generalIsReplacedWithRealChatId() {
        mockGeneralChat();
        UUID messageId = UUID.randomUUID();

        assertEquals(
                "/api/companies/" + companyId + "/chats/" + generalChatId + "/messages/" + messageId + "/readers",
                filter.rewrite("/api/companies/" + companyId + "/chats/general/messages/" + messageId + "/readers")
        );
        assertEquals(
                "/api/companies/" + companyId + "/chats/" + generalChatId,
                filter.rewrite("/api/companies/" + companyId + "/chats/general")
        );
        assertEquals(
                "/api/companies/" + companyId + "/chats/" + generalChatId + "/messages/deleted",
                filter.rewrite("/api/companies/" + companyId + "/chats/general/messages/deleted")
        );
    }

    @Test
    void legacyGeneralAddressesKeepTheirOwnHandlers() {
        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/general/messages"));
        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/general/read"));
        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/general/messages/file"));
        verifyNoInteractions(chatRepository);
    }

    @Test
    void otherPathsAreNotTouched() {
        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/" + UUID.randomUUID() + "/messages"));
        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/generalized"));
        assertNull(filter.rewrite("/api/health"));
        verifyNoInteractions(chatRepository);
    }

    @Test
    void unknownCompanyIsLeftAsIs() {
        when(chatRepository.findByCompanyIdAndType(companyId, "GENERAL")).thenReturn(Optional.empty());

        assertNull(filter.rewrite("/api/companies/" + companyId + "/chats/general/messages/deleted"));
    }

    @Test
    void controllerSeesRewrittenPath() throws Exception {
        mockGeneralChat();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/companies/" + companyId + "/chats/general/messages/" + UUID.randomUUID());
        request.setServletPath(request.getRequestURI());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        String seen = ((jakarta.servlet.http.HttpServletRequest) chain.getRequest()).getRequestURI();
        assertTrue(seen.startsWith("/api/companies/" + companyId + "/chats/" + generalChatId + "/messages/"));
        assertTrue(((jakarta.servlet.http.HttpServletRequest) chain.getRequest()).getRequestURL().toString()
                .endsWith(seen));
    }

    private void mockGeneralChat() {
        ChatEntity chat = new ChatEntity();
        chat.setId(generalChatId);
        when(chatRepository.findByCompanyIdAndType(companyId, "GENERAL")).thenReturn(Optional.of(chat));
    }
}
