package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.MessageReadersResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.ChatReadStateEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageActionsServiceTest {

    @Mock
    private ChatAccessService chatAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatReadStateRepository chatReadStateRepository;
    @Mock
    private ChatMemberRepository chatMemberRepository;
    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @InjectMocks
    private ChatMessageActionsService service;

    private final UUID companyId = UUID.randomUUID();
    private final LocalDateTime sentAt = LocalDateTime.of(2026, 11, 15, 10, 0);

    private ChatEntity chat;
    private UserEntity author;
    private UserEntity colleague;
    private UserEntity director;
    private ChatMessageEntity message;

    @BeforeEach
    void setUp() {
        chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setType("GENERAL");

        author = user("Автор");
        colleague = user("Коллега");
        director = user("Директор");

        message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setChat(chat);
        message.setUser(author);
        message.setIsDeleted(false);
        message.setCreatedAt(sentAt);

        lenient().when(chatAccessService.requireReadableChat(eq(companyId), eq(chat.getId()), any())).thenReturn(chat);
        lenient().when(chatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
    }

    @Test
    void authorDeletesOwnMessageForEveryone() {
        service.deleteForEveryone(companyId, chat.getId(), message.getId(), author.getId());

        assertTrue(message.getIsDeleted());
        assertNotNull(message.getDeletedAt());
        assertEquals(author.getId(), message.getDeletedBy());
        verify(chatMessageRepository).save(message);
    }

    @Test
    void colleagueCannotDeleteSomeoneElsesMessage() {
        when(currentUserService.requireActiveMember(companyId, colleague.getId())).thenReturn(member(colleague, "EMPLOYEE"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deleteForEveryone(companyId, chat.getId(), message.getId(), colleague.getId()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertFalse(message.getIsDeleted());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void directorDeletesAnyMessage() {
        when(currentUserService.requireActiveMember(companyId, director.getId())).thenReturn(member(director, "DIRECTOR"));

        service.deleteForEveryone(companyId, chat.getId(), message.getId(), director.getId());

        assertTrue(message.getIsDeleted());
        assertEquals(director.getId(), message.getDeletedBy());
    }

    @Test
    void deletingTwiceIsNotAnError() {
        message.setIsDeleted(true);

        service.deleteForEveryone(companyId, chat.getId(), message.getId(), colleague.getId());

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void messageFromAnotherChatIsNotFound() {
        ChatEntity other = new ChatEntity();
        other.setId(UUID.randomUUID());
        message.setChat(other);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deleteForEveryone(companyId, chat.getId(), message.getId(), author.getId()));

        assertEquals(ApiErrorCode.MESSAGE_NOT_FOUND, exception.getCode());
    }

    @Test
    void readersAreThoseWhoseReadMarkReachedTheMessage() {
        UserEntity former = user("Бывший сотрудник");
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(List.of(member(author, "EMPLOYEE"), member(colleague, "EMPLOYEE"), member(director, "DIRECTOR")));
        when(chatReadStateRepository.findByChatIdWithUser(chat.getId())).thenReturn(List.of(
                readState(author, sentAt.plusMinutes(5)),      // автор не считается
                readState(colleague, sentAt),                  // прочитал ровно это сообщение
                readState(director, sentAt.minusMinutes(1)),   // остановился раньше
                readState(former, sentAt.plusMinutes(5))       // уже не в компании
        ));

        MessageReadersResponse response = service.getReaders(companyId, chat.getId(), message.getId(), author.getId());

        assertEquals(1, response.readCount());
        assertEquals(2, response.recipientsCount());
        assertEquals(colleague.getId(), response.readers().get(0).userId());
    }

    @Test
    void groupRecipientsAreMembersAndDirector() {
        chat.setType("GROUP");
        UserEntity outsider = user("Не в группе");
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(List.of(member(author, "EMPLOYEE"), member(colleague, "EMPLOYEE"),
                        member(outsider, "EMPLOYEE"), member(director, "DIRECTOR")));
        when(chatMemberRepository.findUserIdsByChatId(chat.getId())).thenReturn(List.of(author.getId(), colleague.getId()));
        when(chatReadStateRepository.findByChatIdWithUser(chat.getId())).thenReturn(List.of());

        MessageReadersResponse response = service.getReaders(companyId, chat.getId(), message.getId(), author.getId());

        assertEquals(0, response.readCount());
        assertEquals(2, response.recipientsCount()); // коллега и директор
    }

    @Test
    void firstCheckWithoutSinceOnlyReturnsServerTime() {
        var response = service.getDeletedSince(companyId, chat.getId(), author.getId(), null);

        assertTrue(response.messageIds().isEmpty());
        assertNotNull(response.checkedAt());
        verify(chatMessageRepository, never()).findDeletedIdsSince(any(), any(), any());
    }

    private static UserEntity user(String name) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        return user;
    }

    private static CompanyMemberEntity member(UserEntity user, String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setCode(roleCode);
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setUser(user);
        member.setRole(role);
        return member;
    }

    private static ChatReadStateEntity readState(UserEntity user, LocalDateTime readUpTo) {
        ChatReadStateEntity state = new ChatReadStateEntity();
        state.setUser(user);
        state.setLastReadMessageCreatedAt(readUpTo);
        state.setUpdatedAt(readUpTo.plusSeconds(30));
        return state;
    }
}
