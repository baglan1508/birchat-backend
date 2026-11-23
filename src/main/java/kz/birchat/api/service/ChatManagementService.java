package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.AddChatMembersRequest;
import kz.birchat.api.dto.ChatDetailsResponse;
import kz.birchat.api.dto.ChatMemberResponse;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.ChatPeerResponse;
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
    private final ChatMessageActionsService chatMessageActionsService;

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> getChats(UUID companyId, UUID userId) {
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);
        boolean director = CurrentUserService.isDirector(member);

        return chatRepository.findAccessibleChats(companyId, userId, director)
                .stream()
                .map(chat -> toSummary(companyId, chat, userId))
                // Личный чат появляется в списке с первым сообщением: его создают в момент открытия.
                .filter(summary -> !ChatType.DIRECT.name().equals(summary.type()) || summary.lastMessageAt() != null)
                .toList();
    }

    /**
     * Личный чат с коллегой: существующий или новый (на пару людей в компании — один чат).
     * Написать можно любому сотруднику компании, кроме себя.
     */
    @Transactional
    public ChatDetailsResponse openDirectChat(UUID companyId, UUID userId, UUID peerId) {
        CompanyMemberEntity me = currentUserService.requireActiveMember(companyId, userId);

        if (peerId == null || peerId.equals(userId)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "Нельзя написать самому себе");
        }

        CompanyMemberEntity peer = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, peerId, STATUS_ACTIVE)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Сотрудник не найден в компании"
                ));

        String key = directKey(userId, peerId);

        ChatEntity chat = chatRepository.findByCompanyIdAndDirectKey(companyId, key)
                .orElseGet(() -> {
                    LocalDateTime now = TimeUtils.utcNow();

                    ChatEntity created = new ChatEntity();
                    created.setId(UUID.randomUUID());
                    created.setCompany(me.getCompany());
                    created.setName(null);
                    created.setType(ChatType.DIRECT.name());
                    created.setDirectKey(key);
                    created.setCreatedBy(me.getUser());
                    created.setCreatedAt(now);

                    ChatEntity saved = chatRepository.save(created);
                    chatMemberRepository.saveAll(List.of(
                            newMember(saved, me.getUser(), me.getUser(), now),
                            newMember(saved, peer.getUser(), me.getUser(), now)
                    ));
                    return saved;
                });

        return toDetails(companyId, chat, false, userId);
    }

    static String directKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    @Transactional(readOnly = true)
    public ChatDetailsResponse getChat(UUID companyId, UUID chatId, UUID userId) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);

        return toDetails(companyId, chat, ChatAccessService.canManageGroup(chat, member, userId), userId);
    }

    /**
     * Новая группа. Создаёт директор, а если в компании включено membersCanCreateGroups — любой
     * сотрудник; создатель становится участником и управляет своей группой.
     */
    @Transactional
    public ChatDetailsResponse createGroupChat(
            UUID companyId,
            UUID creatorId,
            CreateGroupChatRequest request
    ) {
        CompanyMemberEntity creator = currentUserService.requireActiveMember(companyId, creatorId);

        if (!CurrentUserService.isDirector(creator)
                && (creator.getCompany() == null || !creator.getCompany().isMembersCanCreateGroups())) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Группы в этой компании создаёт директор"
            );
        }

        String name = request.name() == null ? "" : request.name().trim();

        if (name.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "name обязателен");
        }

        Set<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(creatorId);

        if (request.memberUserIds() != null) {
            memberIds.addAll(request.memberUserIds());
        }

        Map<UUID, CompanyMemberEntity> companyMembers = requireCompanyMembers(companyId, memberIds);

        LocalDateTime now = TimeUtils.utcNow();

        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(creator.getCompany());
        chat.setName(name);
        chat.setType(ChatType.GROUP.name());
        chat.setCreatedBy(creator.getUser());
        chat.setCreatedAt(now);

        ChatEntity saved = chatRepository.save(chat);

        List<ChatMemberEntity> members = memberIds.stream()
                .map(id -> newMember(saved, companyMembers.get(id).getUser(), creator.getUser(), now))
                .toList();

        chatMemberRepository.saveAll(members);

        return toDetails(companyId, saved, true, creatorId);
    }

    @Transactional
    public ChatDetailsResponse addMembers(
            UUID companyId,
            UUID chatId,
            UUID managerId,
            AddChatMembersRequest request
    ) {
        Managed managed = requireManagedGroup(companyId, chatId, managerId);
        ChatEntity chat = managed.chat();

        Set<UUID> requested = new LinkedHashSet<>(request.userIds());
        Map<UUID, CompanyMemberEntity> companyMembers = requireCompanyMembers(companyId, requested);

        Set<UUID> alreadyInChat = Set.copyOf(chatMemberRepository.findExistingUserIds(chat.getId(), requested));
        LocalDateTime now = TimeUtils.utcNow();

        List<ChatMemberEntity> newMembers = requested.stream()
                .filter(id -> !alreadyInChat.contains(id))
                .map(id -> newMember(chat, companyMembers.get(id).getUser(), managed.member().getUser(), now))
                .toList();

        chatMemberRepository.saveAll(newMembers);

        return toDetails(companyId, chat, true, managerId);
    }

    /** Переименовать группу — директор или создатель. Общий и личный чат не переименовываются. */
    @Transactional
    public ChatDetailsResponse renameChat(UUID companyId, UUID chatId, UUID managerId, String name) {
        ChatEntity chat = requireManagedGroup(companyId, chatId, managerId, "Общий чат нельзя переименовать").chat();

        chat.setName(name.trim());
        chatRepository.save(chat);

        return toDetails(companyId, chat, true, managerId);
    }

    @Transactional
    public void removeMember(
            UUID companyId,
            UUID chatId,
            UUID managerId,
            UUID memberUserId
    ) {
        ChatEntity chat = requireManagedGroup(companyId, chatId, managerId).chat();

        ChatMemberEntity member = chatMemberRepository.findByChatIdAndUserId(chat.getId(), memberUserId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не состоит в этом чате"
                ));

        chatMemberRepository.delete(member);
    }

    /**
     * Удалить группу — директор или создатель. Группа скрывается у всех: участники выходят из неё,
     * сообщения и файлы группы больше нигде не видны, открытый чат отвечает 403 CHAT_ACCESS_DENIED.
     *
     * @return кто был в группе — им уходит событие chat.deleted
     */
    @Transactional
    public Set<UUID> deleteGroup(UUID companyId, UUID chatId, UUID managerId) {
        ChatEntity chat = requireManagedGroup(companyId, chatId, managerId, "Общий чат удалить нельзя").chat();

        Set<UUID> participants = chatMessageActionsService.recipients(companyId, chat);

        chat.setDeletedAt(TimeUtils.utcNow());
        chat.setDeletedBy(managerId);
        chat.setPinnedMessage(null);
        chatRepository.save(chat);
        chatMemberRepository.deleteByChatId(chat.getId());

        return participants;
    }

    /**
     * Закрепить сообщение (одно на чат): в общем чате — директор, в группе — директор или создатель,
     * в личном — любой из двоих. Возвращает закреплённое сообщение для события chat.pinned.
     */
    @Transactional
    public ChatMessageResponse pinMessage(UUID companyId, UUID chatId, UUID messageId, UUID userId) {
        ChatEntity chat = requirePinRights(companyId, chatId, userId);

        ChatMessageEntity message = chatMessageRepository.findById(messageId)
                .filter(found -> chat.getId().equals(found.getChat().getId()))
                .filter(found -> !Boolean.TRUE.equals(found.getIsDeleted()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Сообщение не найдено"
                ));

        chat.setPinnedMessage(message);
        chatRepository.save(chat);

        return chatService.toResponses(List.of(message), null).get(0);
    }

    @Transactional
    public void unpinMessage(UUID companyId, UUID chatId, UUID userId) {
        ChatEntity chat = requirePinRights(companyId, chatId, userId);

        chat.setPinnedMessage(null);
        chatRepository.save(chat);
    }

    private ChatEntity requirePinRights(UUID companyId, UUID chatId, UUID userId) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);

        boolean allowed = ChatAccessService.isDirect(chat)
                || (ChatAccessService.isGeneral(chat) && CurrentUserService.isDirector(member))
                || ChatAccessService.canManageGroup(chat, member, userId);

        if (!allowed) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Закрепить сообщение может директор или создатель группы"
            );
        }

        return chat;
    }

    private record Managed(ChatEntity chat, CompanyMemberEntity member) {
    }

    private Managed requireManagedGroup(UUID companyId, UUID chatId, UUID userId) {
        return requireManagedGroup(
                companyId,
                chatId,
                userId,
                "В общий чат входят все сотрудники компании. "
                        + "Чтобы добавить человека, добавьте его в компанию"
        );
    }

    /** Группа этой компании, которой пользователь управляет (директор или создатель). */
    private Managed requireManagedGroup(UUID companyId, UUID chatId, UUID userId, String messageForGeneralChat) {
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);
        ChatEntity chat = requireGroupChat(companyId, chatId, messageForGeneralChat);

        if (!ChatAccessService.canManageGroup(chat, member, userId)) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Управлять группой может директор или её создатель"
            );
        }

        return new Managed(chat, member);
    }

    // ---- внутреннее ----

    private ChatEntity requireGroupChat(UUID companyId, UUID chatId, String messageForGeneralChat) {
        ChatEntity chat = chatAccessService.requireCompanyChat(companyId, chatId);

        if (ChatAccessService.isDirect(chat)) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Личный чат нельзя переименовать и менять в нём участников"
            );
        }

        if (!ChatAccessService.isGroup(chat)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, messageForGeneralChat);
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
                ChatAccessService.isDirect(chat) ? null : chat.getName(),
                membersCount,
                chatService.unreadCount(companyId, chat.getId(), userId),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : TimeUtils.toUtcOffset(lastMessage.getCreatedAt()),
                TimeUtils.toUtcOffset(chat.getCreatedAt()),
                ChatAccessService.isDirect(chat) ? peer(companyId, chat, userId) : null
        );
    }

    /** Собеседник в личном чате — как его видят коллеги. */
    private ChatPeerResponse peer(UUID companyId, ChatEntity chat, UUID userId) {
        return chatMemberRepository.findMembers(chat.getId()).stream()
                .map(ChatMemberEntity::getUser)
                .filter(user -> !user.getId().equals(userId))
                .findFirst()
                .map(user -> {
                    CompanyMemberEntity membership = companyMemberRepository
                            .findByCompanyIdAndUserId(companyId, user.getId())
                            .orElse(null);
                    MemberProfile profile = MemberProfile.of(user, membership);
                    return new ChatPeerResponse(
                            user.getId(),
                            profile.fullName(),
                            profile.displayName(),
                            profile.initials(),
                            profile.avatarUrl()
                    );
                })
                .orElse(null);
    }

    private ChatDetailsResponse toDetails(UUID companyId, ChatEntity chat, boolean canManage, UUID viewerId) {
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
                ChatAccessService.isDirect(chat) ? null : chat.getName(),
                chat.getCreatedBy() == null ? null : chat.getCreatedBy().getId(),
                TimeUtils.toUtcOffset(chat.getCreatedAt()),
                canManage,
                members,
                pinnedMessage(chat, viewerId)
        );
    }

    /** Закреплённое сообщение чата, если оно не удалено. */
    public ChatMessageResponse pinnedMessage(ChatEntity chat, UUID viewerId) {
        ChatMessageEntity pinned = chat.getPinnedMessage();

        if (pinned == null || Boolean.TRUE.equals(pinned.getIsDeleted())) {
            return null;
        }

        return chatService.toResponses(List.of(pinned), viewerId).get(0);
    }

    private ChatMemberResponse toMember(UserEntity user, CompanyMemberEntity membership, LocalDateTime addedAt) {
        MemberProfile profile = MemberProfile.of(user, membership);

        return new ChatMemberResponse(
                user.getId(),
                profile.fullName(),
                profile.displayName(),
                profile.initials(),
                profile.avatarUrl(),
                membership.getRole() == null ? null : membership.getRole().getCode(),
                membership.getPosition(),
                TimeUtils.toUtcOffset(addedAt)
        );
    }
}
