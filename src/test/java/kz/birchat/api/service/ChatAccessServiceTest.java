package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatRepository;
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
class ChatAccessServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @InjectMocks
    private ChatAccessService chatAccessService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void everyEmployeeCanReadGeneralChat() {
        ChatEntity general = chat("GENERAL", companyId);
        mockMember("EMPLOYEE");
        when(chatRepository.findById(general.getId())).thenReturn(Optional.of(general));

        assertSame(general, chatAccessService.requireReadableChat(companyId, general.getId(), userId));
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void employeeOutsideGroupIsDenied() {
        ChatEntity group = chat("GROUP", companyId);
        mockMember("EMPLOYEE");
        when(chatRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(chatMemberRepository.existsByChatIdAndUserId(group.getId(), userId)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> chatAccessService.requireReadableChat(companyId, group.getId(), userId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals(ApiErrorCode.CHAT_ACCESS_DENIED, error.getCode());
    }

    @Test
    void groupMemberCanReadGroup() {
        ChatEntity group = chat("GROUP", companyId);
        mockMember("EMPLOYEE");
        when(chatRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(chatMemberRepository.existsByChatIdAndUserId(group.getId(), userId)).thenReturn(true);

        assertSame(group, chatAccessService.requireReadableChat(companyId, group.getId(), userId));
    }

    @Test
    void directorCanReadAnyGroup() {
        ChatEntity group = chat("GROUP", companyId);
        mockMember("DIRECTOR");
        when(chatRepository.findById(group.getId())).thenReturn(Optional.of(group));

        assertSame(group, chatAccessService.requireReadableChat(companyId, group.getId(), userId));
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void chatOfAnotherCompanyIsNotFound() {
        ChatEntity foreign = chat("GENERAL", UUID.randomUUID());
        mockMember("DIRECTOR");
        when(chatRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        ApiException error = assertThrows(ApiException.class,
                () -> chatAccessService.requireReadableChat(companyId, foreign.getId(), userId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void companyFileIsVisibleToEveryone() {
        CompanyFileEntity file = new CompanyFileEntity();

        assertTrue(chatAccessService.canAccessFile(file, member("EMPLOYEE"), userId));
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void groupFileIsHiddenFromNonMembers() {
        ChatEntity group = chat("GROUP", companyId);
        CompanyFileEntity file = new CompanyFileEntity();
        file.setChat(group);
        when(chatMemberRepository.existsByChatIdAndUserId(group.getId(), userId)).thenReturn(false);

        assertFalse(chatAccessService.canAccessFile(file, member("EMPLOYEE"), userId));
    }

    private void mockMember(String roleCode) {
        when(currentUserService.requireActiveMember(companyId, userId)).thenReturn(member(roleCode));
    }

    private CompanyMemberEntity member(String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setRole(role);
        return member;
    }

    private ChatEntity chat(String type, UUID ownerCompanyId) {
        CompanyEntity company = new CompanyEntity();
        company.setId(ownerCompanyId);

        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(company);
        chat.setType(type);
        chat.setName(type);
        return chat;
    }
}
