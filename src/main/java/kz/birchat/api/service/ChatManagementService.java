package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.AddChatMembersRequest;
import kz.birchat.api.dto.ChatDetailsResponse;
import kz.birchat.api.dto.ChatMemberResponse;
import kz.birchat.api.dto.ChatSummaryResponse;
import kz.birchat.api.dto.CreateGroupChatRequest;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMemberEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Список чатов, групповые чаты и их участники.
 * Создавать группы и менять состав может только директор компании.
 */
@Service
@RequiredArgsConstructor
public class ChatManagementService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final CurrentUserService currentUserService;
    private final ChatAccessService chatAccessService;
    private final ChatService chatService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CompanyMemberRepository companyMemberRepository;

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> getChats(UUID companyId, UUID userId) {
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);
        boolean director = CurrentUserService.isDirector(member);

        return chatRepository.findAccessibleChats(companyId, userId, director)
                .stream()
                .map(chat -> toSummary(companyId, chat, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatDetailsResponse getChat(UUID companyId, UUID chatId, UUID userId) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        boolean director = CurrentUserService.isDirector(
                currentUserService.requireActiveMember(companyId, userId)
        );

        return toDetails(companyId, chat, director);
    }

    @Transactional
    public ChatDetailsResponse createGroupChat(
            UUID companyId,
            UUID directorId,
            CreateGroupChatRequest request
    ) {
        CompanyMemberEntity director = currentUserService.requireDirector(companyId, directorId);

        String name = request.name() == null ? "" : request.name().trim();

        if (name.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "name обязателен");
        }

        Set<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(directorId);

        if (request.memberUserIds() != null) {
            memberIds.addAll(request.memberUserIds());
        }

        Map<UUID, CompanyMemberEntity> companyMembers = requireCompanyMembers(companyId, memberIds);

        LocalDateTime now = TimeUtils.utcNow();

        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(director.getCompany());
        chat.setName(name);
        chat.setType(ChatType.GROUP.name());
        chat.setCreatedBy(director.getUser());
        chat.setCreatedAt(now);

        ChatEntity saved = chatRepository.save(chat);

        List<ChatMemberEntity> members = memberIds.stream()
                .map(id -> newMember(saved, companyMembers.get(id).getUser(), director.getUser(), now))
                .toList();

        chatMemberRepository.saveAll(members);

        return toDetails(companyId, saved, true);
    }

    @Transactional
    public ChatDetailsResponse addMembers(
            UUID companyId,
            UUID chatId,
            UUID directorId,
            AddChatMembersRequest request
    ) {
        CompanyMemberEntity director = currentUserService.requireDirector(companyId, directorId);
        ChatEntity chat = requireGroupChat(companyId, chatId);

        Set<UUID> requested = new LinkedHashSet<>(request.userIds());
        Map<UUID, CompanyMemberEntity> companyMembers = requireCompanyMembers(companyId, requested);

        Set<UUID> alreadyInChat = Set.copyOf(chatMemberRepository.findExistingUserIds(chat.getId(), requested));
        LocalDateTime now = TimeUtils.utcNow();

        List<ChatMemberEntity> newMembers = requested.stream()
                .filter(id -> !alreadyInChat.contains(id))
                .map(id -> newMember(chat, companyMembers.get(id).getUser(), director.getUser(), now))
                .toList();

        chatMemberRepository.saveAll(newMembers);

        return toDetails(companyId, chat, true);
    }

    @Transactional
    public void removeMember(
            UUID companyId,
            UUID chatId,
            UUID directorId,
            UUID memberUserId
    ) {
        currentUserService.requireDirector(companyId, directorId);
        ChatEntity chat = requireGroupChat(companyId, chatId);

        ChatMemberEntity member = chatMemberRepository.findByChatIdAndUserId(chat.getId(), memberUserId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не состоит в этом чате"
                ));

        chatMemberRepository.delete(member);
    }

    // ---- внутреннее ----

    private ChatEntity requireGroupChat(UUID companyId, UUID chatId) {
        ChatEntity chat = chatAccessService.requireCompanyChat(companyId, chatId);

        if (!ChatAccessService.isGroup(chat)) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "В общий чат входят все сотрудники компании. "
                            + "Чтобы добавить человека, добавьте его в компанию"
            );
        }

        return chat;
    }

    /**
     * Все пользователи должны быть активными сотрудниками этой компании.
     */
    private Map<UUID, CompanyMemberEntity> requireCompanyMembers(UUID companyId, Set<UUID> userIds) {
        Map<UUID, CompanyMemberEntity> active = companyMemberRepository
                .findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, STATUS_ACTIVE)
                .stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity(), (a, b) -> a));

        List<UUID> unknown = userIds.stream()
                .filter(id -> id == null || !active.containsKey(id))
                .toList();

        if (!unknown.isEmpty()) {
            throw ApiException.badRequest(
                    ApiErrorCode.NOT_A_MEMBER,
                    "Не являются сотрудниками компании: " + unknown
            );
        }

        return active;
    }

    private ChatMemberEntity newMember(ChatEntity chat, UserEntity user, UserEntity addedBy, LocalDateTime now) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(UUID.randomUUID());
        member.setChat(chat);
        member.setUser(user);
        member.setAddedBy(addedBy);
        member.setCreatedAt(now);
        return member;
    }

    private ChatSummaryResponse toSummary(UUID companyId, ChatEntity chat, UUID userId) {
        List<ChatMessageEntity> last = chatMessageRepository.findLatestGeneralChatMessages(
                companyId,
                chat.getId(),
                PageRequest.of(0, 1)
        );

        ChatMessageEntity lastMessage = last.isEmpty() ? null : last.get(0);

        long membersCount = ChatAccessService.isGeneral(chat)
                ? companyMemberRepository.countByCompanyIdAndStatus(companyId, STATUS_ACTIVE)
                : chatMemberRepository.countByChatId(chat.getId());

        return new ChatSummaryResponse(
                chat.getId(),
                chat.getType(),
                chat.getName(),
                membersCount,
                chatService.unreadCount(companyId, chat.getId(), userId),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : TimeUtils.toUtcOffset(lastMessage.getCreatedAt()),
                TimeUtils.toUtcOffset(chat.getCreatedAt())
        );
    }

    private ChatDetailsResponse toDetails(UUID companyId, ChatEntity chat, boolean director) {
        Map<UUID, CompanyMemberEntity> companyMembers = companyMemberRepository
                .findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, STATUS_ACTIVE)
                .stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity(), (a, b) -> a));

        List<ChatMemberResponse> members;

        if (ChatAccessService.isGeneral(chat)) {
            members = companyMembers.values().stream()
                    .map(m -> toMember(m.getUser(), m, m.getJoinedAt()))
                    .toList();
        } else {
            members = chatMemberRepository.findMembers(chat.getId()).stream()
                    .filter(cm -> companyMembers.containsKey(cm.getUser().getId()))
                    .map(cm -> toMember(cm.getUser(), companyMembers.get(cm.getUser().getId()), cm.getCreatedAt()))
                    .toList();
        }

        return new ChatDetailsResponse(
                chat.getId(),
                chat.getType(),
                chat.getName(),
                chat.getCreatedBy() == null ? null : chat.getCreatedBy().getId(),
                TimeUtils.toUtcOffset(chat.getCreatedAt()),
                director && ChatAccessService.isGroup(chat),
                members
        );
    }

    private ChatMemberResponse toMember(UserEntity user, CompanyMemberEntity membership, LocalDateTime addedAt) {
        return new ChatMemberResponse(
                user.getId(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                user.getAvatarUrl(),
                membership.getRole() == null ? null : membership.getRole().getCode(),
                membership.getPosition(),
                TimeUtils.toUtcOffset(addedAt)
        );
    }
}
