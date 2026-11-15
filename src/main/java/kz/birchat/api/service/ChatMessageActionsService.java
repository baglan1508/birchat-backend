package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.DeletedMessagesResponse;
import kz.birchat.api.dto.MessageReadersResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.ChatReadStateEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Действия над сообщением: удалить у всех, кто прочитал.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageActionsService {

    private static final int MAX_DELETED_IDS = 500;

    private final ChatAccessService chatAccessService;
    private final CurrentUserService currentUserService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final CompanyMemberRepository companyMemberRepository;

    /**
     * Удалить сообщение у всех. Может автор сообщения или директор компании.
     * Сообщение остаётся в базе с пометкой: пропадает из чата, поиска, сводок и контекста AI.
     * Повторное удаление — не ошибка.
     */
    @Transactional
    public void deleteForEveryone(UUID companyId, UUID chatId, UUID messageId, UUID userId) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        ChatMessageEntity message = requireMessage(chat, messageId);

        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            return;
        }

        boolean author = message.getUser() != null && userId.equals(message.getUser().getId());

        if (!author && !CurrentUserService.isDirector(currentUserService.requireActiveMember(companyId, userId))) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Удалить сообщение может его автор или директор"
            );
        }

        LocalDateTime now = TimeUtils.utcNow();
        message.setIsDeleted(true);
        message.setDeletedAt(now);
        message.setDeletedBy(userId);
        message.setUpdatedAt(now);
        chatMessageRepository.save(message);
    }

    /** Сообщения чата, удалённые у всех после since. */
    @Transactional(readOnly = true)
    public DeletedMessagesResponse getDeletedSince(UUID companyId, UUID chatId, UUID userId, OffsetDateTime since) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        LocalDateTime now = TimeUtils.utcNow();

        // Первый запрос без since: только отдаём серверное время — от него приложение считает дальше.
        // Свои часы телефона использовать нельзя: они могут спешить, и удаление потеряется.
        if (since == null) {
            return new DeletedMessagesResponse(List.of(), TimeUtils.toUtcOffset(now));
        }

        List<UUID> ids = chatMessageRepository.findDeletedIdsSince(
                chat.getId(),
                since.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                PageRequest.of(0, MAX_DELETED_IDS)
        );

        return new DeletedMessagesResponse(ids, TimeUtils.toUtcOffset(now));
    }

    /**
     * Кто прочитал сообщение. Считается по отметкам «прочитано до сообщения N»: прочитал тот,
     * чья отметка стоит на этом сообщении или позже. Автор и те, кто уже не в чате, не учитываются.
     */
    @Transactional(readOnly = true)
    public MessageReadersResponse getReaders(UUID companyId, UUID chatId, UUID messageId, UUID userId) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        ChatMessageEntity message = requireMessage(chat, messageId);

        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw ApiException.notFound(ApiErrorCode.MESSAGE_NOT_FOUND, "Сообщение не найдено");
        }

        UUID authorId = message.getUser() == null ? null : message.getUser().getId();
        Set<UUID> recipients = recipients(companyId, chat);
        recipients.remove(authorId);

        List<MessageReadersResponse.Reader> readers = chatReadStateRepository
                .findByChatIdWithUser(chat.getId())
                .stream()
                .filter(state -> recipients.contains(state.getUser().getId()))
                .filter(state -> hasRead(state, message))
                .sorted(Comparator.comparing(ChatReadStateEntity::getUpdatedAt))
                .map(this::toReader)
                .toList();

        return new MessageReadersResponse(message.getId(), readers.size(), recipients.size(), readers);
    }

    static boolean hasRead(ChatReadStateEntity state, ChatMessageEntity message) {
        LocalDateTime readUpTo = state.getLastReadMessageCreatedAt();

        return readUpTo != null
                && message.getCreatedAt() != null
                && !readUpTo.isBefore(message.getCreatedAt());
    }

    /** Кто сейчас в чате: общий — все активные сотрудники; группа — участники и директор. */
    private Set<UUID> recipients(UUID companyId, ChatEntity chat) {
        List<CompanyMemberEntity> active = companyMemberRepository
                .findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE");
        Set<UUID> result = new HashSet<>();

        if (ChatAccessService.isGeneral(chat)) {
            active.forEach(member -> result.add(member.getUser().getId()));
            return result;
        }

        Set<UUID> chatMembers = new HashSet<>(chatMemberRepository.findUserIdsByChatId(chat.getId()));

        for (CompanyMemberEntity member : active) {
            UUID id = member.getUser().getId();

            if (chatMembers.contains(id) || CurrentUserService.isDirector(member)) {
                result.add(id);
            }
        }

        return result;
    }

    private ChatMessageEntity requireMessage(ChatEntity chat, UUID messageId) {
        return chatMessageRepository.findById(messageId)
                .filter(message -> chat.getId().equals(message.getChat().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Сообщение не найдено"
                ));
    }

    private MessageReadersResponse.Reader toReader(ChatReadStateEntity state) {
        UserEntity user = state.getUser();

        return new MessageReadersResponse.Reader(
                user.getId(),
                user.getDisplayName(),
                user.getInitials(),
                user.getAvatarUrl(),
                TimeUtils.toUtcOffset(state.getUpdatedAt())
        );
    }
}
