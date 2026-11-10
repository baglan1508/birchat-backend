package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.AddChatMembersRequest;
import kz.birchat.api.dto.ChatDetailsResponse;
import kz.birchat.api.dto.CreateGroupChatRequest;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMemberEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatManagementServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ChatAccessService chatAccessService;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @InjectMocks
    private ChatManagementService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID directorId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void nonDirectorCannotCreateGroup() {
        when(currentUserService.requireDirector(companyId, employeeId))
                .thenThrow(ApiException.forbidden(ApiErrorCode.FORBIDDEN, "Только директор"));

        ApiException error = assertThrows(ApiException.class, () -> service.createGroupChat(
                companyId, employeeId, new CreateGroupChatRequest("Склад", List.of())
        ));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(chatRepository, chatMemberRepository);
    }

    @Test
    void cannotAddPeopleFromOutsideTheCompany() {
        when(currentUserService.requireDirector(companyId, directorId))
                .thenReturn(companyMember(directorId, "DIRECTOR"));
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(List.of(companyMember(directorId, "DIRECTOR")));

        UUID stranger = UUID.randomUUID();

        ApiException error = assertThrows(ApiException.class, () -> service.createGroupChat(
                companyId, directorId, new CreateGroupChatRequest("Склад", List.of(stranger))
        ));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, error.getCode());
        verifyNoInteractions(chatRepository, chatMemberRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void directorCreatesGroupAndBecomesMember() {
        CompanyMemberEntity director = companyMember(directorId, "DIRECTOR");
        CompanyMemberEntity employee = companyMember(employeeId, "EMPLOYEE");

        when(currentUserService.requireDirector(companyId, directorId)).thenReturn(director);
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(List.of(director, employee));
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMemberRepository.findMembers(any())).thenReturn(List.of());

        ChatDetailsResponse response = service.createGroupChat(
                companyId, directorId, new CreateGroupChatRequest("  Склад  ", List.of(employeeId, employeeId))
        );

        assertEquals("GROUP", response.type());
        assertEquals("Склад", response.name());
        assertTrue(response.canManage());

        ArgumentCaptor<List<ChatMemberEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMemberRepository).saveAll(captor.capture());

        List<UUID> memberIds = captor.getValue().stream()
                .map(m -> m.getUser().getId())
                .toList();

        assertEquals(List.of(directorId, employeeId), memberIds);
    }

    @Test
    void generalChatMembersAreManagedThroughEmployees() {
        ChatEntity general = new ChatEntity();
        general.setId(UUID.randomUUID());
        general.setType("GENERAL");

        when(currentUserService.requireDirector(companyId, directorId))
                .thenReturn(companyMember(directorId, "DIRECTOR"));
        when(chatAccessService.requireCompanyChat(companyId, general.getId())).thenReturn(general);

        ApiException error = assertThrows(ApiException.class, () -> service.addMembers(
                companyId, general.getId(), directorId, new AddChatMembersRequest(List.of(employeeId))
        ));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(chatMemberRepository, never()).saveAll(anyList());
    }

    private CompanyMemberEntity companyMember(UUID userId, String roleCode) {
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);

        UserEntity user = new UserEntity();
        user.setId(userId);

        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setCompany(company);
        member.setUser(user);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }
}
