package kz.birchat.api.service;

import kz.birchat.api.dto.ChatAttachmentResponse;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.ChatReadStateResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
import kz.birchat.api.dto.CreateFileMessageRequest;
import kz.birchat.api.dto.EditMessageRequest;
import kz.birchat.api.dto.UpdatedMessagesResponse;
import kz.birchat.api.dto.MarkChatReadRequest;
import kz.birchat.api.entity.ChatAttachmentEntity;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.ChatMessageMentionEntity;
import kz.birchat.api.entity.ChatMessageReactionEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.ChatReadStateEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageMentionRepository;
import kz.birchat.api.repository.ChatMessageReactionRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сообщения в чатах компании (общем и групповых). Доступ проверяет ChatAccessService.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatAccessService chatAccessService;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final CompanyFileRepository companyFileRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatMessageMentionRepository chatMessageMentionRepository;
    private final ChatMessageActionsService chatMessageActionsService;
    private final CompanyMemberRepository companyMemberRepository;
    private final ChatMessageReactionRepository chatMessageReactionRepository;

    /** Сколько времени автор может изменить сообщение. */
    static final Duration EDIT_WINDOW = Duration.ofHours(48);
    static final int REPLY_PREVIEW_LENGTH = 100;
    private static final int MAX_UPDATED_MESSAGES = 200;

    // ---- общий чат: старые эндпоинты /chats/general/* ----

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(
            UUID companyId,
            UUID userId,
            UUID after,
            UUID before,
            Integer limit
    ) {
        return getGeneralChatMessages(companyId, userId, after, before, null, limit);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(
            UUID companyId,
            UUID userId,
            UUID after,
            UUID before,
            UUID around,
            Integer limit
    ) {
        UUID chatId = chatAccessService.requireGeneralChat(companyId).getId();
        return getMessages(companyId, chatId, userId, after, before, around, limit);
    }

    @Transactional
    public ChatMessageResponse createGeneralChatMessage(
            UUID companyId,
            UUID userId,
            CreateChatMessageRequest request
    ) {
        UUID chatId = chatAccessService.requireGeneralChat(companyId).getId();
        return createMessage(companyId, chatId, userId, request);
    }

    @Transactional
    public ChatMessageResponse createGeneralChatFileMessage(
            UUID companyId,
            UUID userId,
            CreateFileMessageRequest request
    ) {
        UUID chatId = chatAccessService.requireGeneralChat(companyId).getId();
        return createFileMessage(companyId, chatId, userId, request);
    }

    @Transactional
    public ChatReadStateResponse markGeneralChatAsRead(
            UUID companyId,
            UUID userId,
            MarkChatReadRequest request
    ) {
        UUID chatId = chatAccessService.requireGeneralChat(companyId).getId();
        return markAsRead(companyId, chatId, userId, request);
    }

    // ---- любой чат по chatId ----

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(
            UUID companyId,
            UUID chatId,
            UUID userId,
            UUID after,
            UUID before,
            Integer limit
    ) {
        return getMessages(companyId, chatId, userId, after, before, null, limit);
    }

    /**
     * Страница сообщений по возрастанию времени: последние; после after; до before;
     * или вокруг around — половина до, само сообщение и остальное после (переход из поиска).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(
            UUID companyId,
            UUID chatId,
            UUID userId,
            UUID after,
            UUID before,
            UUID around,
            Integer limit
    ) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);

        int cursors = (after == null ? 0 : 1) + (before == null ? 0 : 1) + (around == null ? 0 : 1);

        if (cursors > 1) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Можно указать только один из after, before, around"
            );
        }

        int safeLimit = normalizeLimit(limit);
        List<ChatMessageEntity> messages;

        if (around != null) {
            ChatMessageEntity center = findMessageCursor(companyId, chat.getId(), around, "around");
            int olderCount = safeLimit / 2;

            List<ChatMessageEntity> older = olderCount == 0 ? new ArrayList<>() : new ArrayList<>(
                    chatMessageRepository.findGeneralChatMessagesBefore(
                            companyId, chat.getId(), center.getCreatedAt(), center.getId(),
                            PageRequest.of(0, olderCount)));
            Collections.reverse(older);

            int newerCount = safeLimit - older.size() - 1;
            List<ChatMessageEntity> newer = newerCount <= 0 ? List.of() :
                    chatMessageRepository.findGeneralChatMessagesAfter(
                            companyId, chat.getId(), center.getCreatedAt(), center.getId(),
                            PageRequest.of(0, newerCount));

            messages = new ArrayList<>(older);

            if (!Boolean.TRUE.equals(center.getIsDeleted())) {
                messages.add(center);
            }

            messages.addAll(newer);
        } else if (after != null) {
            ChatMessageEntity afterMessage = findMessageCursor(companyId, chat.getId(), after, "after");

            messages = chatMessageRepository.findGeneralChatMessagesAfter(
                    companyId,
                    chat.getId(),
                    afterMessage.getCreatedAt(),
                    afterMessage.getId(),
                    PageRequest.of(0, safeLimit)
            );
        } else if (before != null) {
            ChatMessageEntity beforeMessage = findMessageCursor(companyId, chat.getId(), before, "before");

            messages = chatMessageRepository.findGeneralChatMessagesBefore(
                    companyId,
                    chat.getId(),
                    beforeMessage.getCreatedAt(),
                    beforeMessage.getId(),
                    PageRequest.of(0, safeLimit)
            );

            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository.findLatestGeneralChatMessages(
                    companyId,
                    chat.getId(),
                    PageRequest.of(0, safeLimit)
            );

            Collections.reverse(messages);
        }

        return toResponses(messages, userId);
    }

    @Transactional
    public ChatMessageResponse createMessage(
            UUID companyId,
            UUID chatId,
            UUID userId,
            CreateChatMessageRequest request
    ) {
        ChatEntity chat = chatAccessService.requireWritableChat(companyId, chatId, userId);
        UserEntity user = findUser(userId);
        LocalDateTime now = TimeUtils.utcNow();

        ChatMessageEntity replyTo = resolveReplyTo(chat, request.replyToMessageId());
        ChatMessageEntity source = resolveForwardSource(companyId, userId, request.forwardedFromMessageId());
        String text = request.text() == null ? "" : request.text().trim();

        List<ChatAttachmentEntity> sourceAttachments = source == null
                ? List.of()
                : chatAttachmentRepository.findByMessageIds(List.of(source.getId()));

        if (source != null && text.isEmpty() && source.getContent() != null) {
            text = source.getContent();
        }

        if (text.isEmpty() && sourceAttachments.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "text не должен быть пустым");
        }

        ChatMessageType type = source == null || source.getType() == ChatMessageType.SYSTEM
                ? ChatMessageType.TEXT
                : source.getType();

        ChatMessageEntity message = newMessage(chat, user, type, text, now);
        message.setReplyToMessage(replyTo);
        // Пересылка пересланного указывает на первоисточник: «переслано от» — настоящий автор.
        message.setForwardedFromMessage(source == null ? null
                : source.getForwardedFromMessage() != null ? source.getForwardedFromMessage() : source);

        ChatMessageEntity saved = chatMessageRepository.save(message);

        // Пересылка с файлом: к новому сообщению прикрепляется тот же файл.
        for (ChatAttachmentEntity sourceAttachment : sourceAttachments) {
            chatAttachmentRepository.save(newAttachment(chat, saved, sourceAttachment.getFile(), now));
        }

        saveMentions(saved, companyId, chat, userId, request.mentions(), now);

        return toResponses(List.of(saved), userId).get(0);
    }

    /**
     * Изменить текст (у файла — подпись). Только автор и не позже 48 часов после отправки.
     * mentions, если переданы, заменяют прежний список.
     */
    @Transactional
    public ChatMessageResponse editMessage(
            UUID companyId,
            UUID chatId,
            UUID messageId,
            UUID userId,
            EditMessageRequest request
    ) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        ChatMessageEntity message = findMessageCursor(companyId, chat.getId(), messageId, "messageId");

        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw ApiException.notFound(ApiErrorCode.MESSAGE_NOT_FOUND, "Сообщение не найдено");
        }

        if (message.getUser() == null || !userId.equals(message.getUser().getId())) {
            throw ApiException.forbidden(ApiErrorCode.FORBIDDEN, "Изменить сообщение может только автор");
        }

        LocalDateTime now = TimeUtils.utcNow();

        if (message.getCreatedAt() != null && message.getCreatedAt().plus(EDIT_WINDOW).isBefore(now)) {
            throw ApiException.forbidden(ApiErrorCode.EDIT_EXPIRED, "Сообщение можно изменить в течение 48 часов");
        }

        String text = request == null || request.text() == null ? "" : request.text().trim();

        if (text.isEmpty() && message.getType() == ChatMessageType.TEXT) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "text не должен быть пустым");
        }

        message.setContent(text);
        message.setEditedAt(now);
        message.setUpdatedAt(now);
        ChatMessageEntity saved = chatMessageRepository.save(message);

        if (request != null && request.mentions() != null) {
            chatMessageMentionRepository.deleteByMessageId(saved.getId());
            chatMessageMentionRepository.flush();
            saveMentions(saved, companyId, chat, userId, request.mentions(), now);
        }

        return toResponses(List.of(saved), userId).get(0);
    }

    /**
     * Сообщения чата, изменённые после since (опрос для старых клиентов и после переподключения).
     * Без since — пустой список и серверное время: часы телефона для since не годятся.
     */
    @Transactional(readOnly = true)
    public UpdatedMessagesResponse getUpdatedSince(UUID companyId, UUID chatId, UUID userId, OffsetDateTime since) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        LocalDateTime now = TimeUtils.utcNow();

        if (since == null) {
            return new UpdatedMessagesResponse(List.of(), TimeUtils.toUtcOffset(now));
        }

        List<ChatMessageEntity> edited = chatMessageRepository.findEditedSince(
                chat.getId(),
                since.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                PageRequest.of(0, MAX_UPDATED_MESSAGES)
        );

        return new UpdatedMessagesResponse(toResponses(edited, userId), TimeUtils.toUtcOffset(now));
    }

    @Transactional
    public ChatMessageResponse createFileMessage(
            UUID companyId,
            UUID chatId,
            UUID userId,
            CreateFileMessageRequest request
    ) {
        ChatEntity chat = chatAccessService.requireWritableChat(companyId, chatId, userId);
        UserEntity user = findUser(userId);
        ChatMessageEntity replyTo = resolveReplyTo(chat, request.replyToMessageId());

        CompanyFileEntity file = companyFileRepository.findById(request.fileId())
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .filter(item -> CompanyFileService.STATUS_READY.equals(item.getStatus()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл не найден"
                ));

        bindFileToChat(file, chat, userId);

        LocalDateTime now = TimeUtils.utcNow();
        String text = request.text() == null ? "" : request.text().trim();

        ChatMessageEntity message = newMessage(chat, user, resolveMessageType(file.getContentType()), text, now);
        message.setReplyToMessage(replyTo);
        ChatMessageEntity savedMessage = chatMessageRepository.save(message);

        chatAttachmentRepository.save(newAttachment(chat, savedMessage, file, now));
        saveMentions(savedMessage, companyId, chat, userId, request.mentions(), now);

        return toResponses(List.of(savedMessage), userId).get(0);
    }

    @Transactional
    public ChatReadStateResponse markAsRead(
            UUID companyId,
            UUID chatId,
            UUID userId,
            MarkChatReadRequest request
    ) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        ChatMessageEntity message = findMessageCursor(companyId, chat.getId(), request.messageId(), "messageId");
        UserEntity user = findUser(userId);

        ChatReadStateEntity state = chatReadStateRepository
                .findState(companyId, chat.getId(), userId)
                .orElseGet(() -> {
                    ChatReadStateEntity newState = new ChatReadStateEntity();
                    newState.setId(UUID.randomUUID());
                    newState.setCompany(chat.getCompany());
                    newState.setChat(chat);
                    newState.setUser(user);
                    return newState;
                });

        if (shouldUpdateReadState(state, message)) {
            state.setLastReadMessage(message);
            state.setLastReadMessageCreatedAt(message.getCreatedAt());
        }

        state.setUpdatedAt(TimeUtils.utcNow());

        ChatReadStateEntity saved = chatReadStateRepository.save(state);

        return new ChatReadStateResponse(
                chat.getId(),
                userId,
                saved.getLastReadMessage() != null ? saved.getLastReadMessage().getId() : null,
                TimeUtils.toUtcOffset(saved.getLastReadMessageCreatedAt()),
                calculateUnreadCount(companyId, chat.getId(), userId, saved)
        );
    }

    /**
     * Непрочитанные сообщения пользователя в чате (без проверки доступа — её делает вызывающий).
     */
    @Transactional(readOnly = true)
    public Long unreadCount(UUID companyId, UUID chatId, UUID userId) {
        ChatReadStateEntity state = chatReadStateRepository
                .findState(companyId, chatId, userId)
                .orElse(null);

        return calculateUnreadCount(companyId, chatId, userId, state);
    }

    // ---- внутреннее ----

    /**
     * Файл группы можно прикрепить только в эту же группу.
     * Файл компании, только что загруженный автором без chatId и ещё нигде не прикреплённый,
     * при отправке в группу или личный чат становится файлом этого чата, чтобы не светиться в общем списке.
     */
    private void bindFileToChat(CompanyFileEntity file, ChatEntity chat, UUID userId) {
        ChatEntity fileChat = file.getChat();

        if (fileChat != null) {
            if (!fileChat.getId().equals(chat.getId())) {
                throw ApiException.badRequest(
                        ApiErrorCode.VALIDATION,
                        "Файл принадлежит другому чату"
                );
            }

            return;
        }

        boolean ownFreshUpload = (ChatAccessService.isGroup(chat) || ChatAccessService.isDirect(chat))
                && file.getUploadedBy() != null
                && userId.equals(file.getUploadedBy().getId())
                && !chatAttachmentRepository.existsByFileId(file.getId());

        if (ownFreshUpload) {
            file.setChat(chat);
            file.setUpdatedAt(TimeUtils.utcNow());
            companyFileRepository.save(file);
        }
    }

    /** Ответить можно только на неудалённое сообщение этого же чата. */
    private ChatMessageEntity resolveReplyTo(ChatEntity chat, UUID replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }

        return chatMessageRepository.findById(replyToMessageId)
                .filter(message -> chat.getId().equals(message.getChat().getId()))
                .filter(message -> !Boolean.TRUE.equals(message.getIsDeleted()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Сообщение для ответа не найдено"
                ));
    }

    /** Переслать можно неудалённое сообщение из чата этой компании, который виден отправителю. */
    private ChatMessageEntity resolveForwardSource(UUID companyId, UUID userId, UUID messageId) {
        if (messageId == null) {
            return null;
        }

        ChatMessageEntity source = chatMessageRepository.findAllWithUserAndChatByIdIn(List.of(messageId))
                .stream()
                .findFirst()
                .filter(message -> companyId.equals(message.getCompany().getId()))
                .filter(message -> !Boolean.TRUE.equals(message.getIsDeleted()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Пересылаемое сообщение не найдено"
                ));

        chatAccessService.requireReadableChat(companyId, source.getChat().getId(), userId);
        return source;
    }

    /** Упомянуть можно только участников чата (не себя); остальные id молча отбрасываются. */
    private void saveMentions(
            ChatMessageEntity message,
            UUID companyId,
            ChatEntity chat,
            UUID authorId,
            List<UUID> requested,
            LocalDateTime now
    ) {
        if (requested == null || requested.isEmpty()) {
            return;
        }

        Set<UUID> participants = chatMessageActionsService.recipients(companyId, chat);
        Set<UUID> mentioned = new LinkedHashSet<>();

        for (UUID id : requested) {
            if (id != null && !id.equals(authorId) && participants.contains(id)) {
                mentioned.add(id);
            }
        }

        for (UUID id : mentioned) {
            ChatMessageMentionEntity mention = new ChatMessageMentionEntity();
            mention.setId(UUID.randomUUID());
            mention.setMessage(message);
            mention.setUser(userRepository.getReferenceById(id));
            mention.setCreatedAt(now);
            chatMessageMentionRepository.save(mention);
        }
    }

    private ChatAttachmentEntity newAttachment(
            ChatEntity chat,
            ChatMessageEntity message,
            CompanyFileEntity file,
            LocalDateTime now
    ) {
        ChatAttachmentEntity attachment = new ChatAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setCompany(chat.getCompany());
        attachment.setChat(chat);
        attachment.setMessage(message);
        attachment.setFile(file);
        attachment.setCreatedAt(now);
        return attachment;
    }

    private ChatMessageEntity newMessage(
            ChatEntity chat,
            UserEntity user,
            ChatMessageType type,
            String text,
            LocalDateTime now
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setCompany(chat.getCompany());
        message.setChat(chat);
        message.setUser(user);
        message.setType(type);
        message.setContent(text);
        message.setIsDeleted(false);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        return message;
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }

        if (limit < 1) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "limit должен быть больше 0"
            );
        }

        return Math.min(limit, 100);
    }

    private ChatMessageEntity findMessageCursor(
            UUID companyId,
            UUID chatId,
            UUID messageId,
            String cursorName
    ) {
        return chatMessageRepository.findById(messageId)
                .filter(message -> companyId.equals(message.getCompany().getId()))
                .filter(message -> chatId.equals(message.getChat().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Сообщение " + cursorName + " не найдено"
                ));
    }

    private boolean shouldUpdateReadState(ChatReadStateEntity state, ChatMessageEntity message) {
        if (state.getLastReadMessageCreatedAt() == null || state.getLastReadMessage() == null) {
            return true;
        }

        int timeCompare = message.getCreatedAt().compareTo(state.getLastReadMessageCreatedAt());

        if (timeCompare > 0) {
            return true;
        }

        return timeCompare == 0
                && message.getId().compareTo(state.getLastReadMessage().getId()) > 0;
    }

    private Long calculateUnreadCount(
            UUID companyId,
            UUID chatId,
            UUID userId,
            ChatReadStateEntity state
    ) {
        if (state == null || state.getLastReadMessageCreatedAt() == null || state.getLastReadMessage() == null) {
            return chatMessageRepository.countUnreadGeneralChatMessagesAll(companyId, chatId, userId);
        }

        return chatMessageRepository.countUnreadGeneralChatMessagesAfter(
                companyId,
                chatId,
                userId,
                state.getLastReadMessageCreatedAt(),
                state.getLastReadMessage().getId()
        );
    }

    private ChatMessageType resolveMessageType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return ChatMessageType.DOCUMENT;
        }

        String value = contentType.toLowerCase();

        if (value.startsWith("image/")) {
            return ChatMessageType.IMAGE;
        }

        if (value.startsWith("audio/")) {
            return ChatMessageType.VOICE;
        }

        return ChatMessageType.DOCUMENT;
    }

    /**
     * Ответы для списка сообщений. Вложения, цитаты, пересылки и упоминания грузятся пачками —
     * по запросу на вид, а не на каждое сообщение.
     */
    List<ChatMessageResponse> toResponses(List<ChatMessageEntity> messages) {
        return toResponses(messages, null);
    }

    /** viewerId — кто смотрит: для отметки «моя реакция» (null — без отметок). */
    public List<ChatMessageResponse> toResponses(List<ChatMessageEntity> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }

        List<UUID> messageIds = messages.stream()
                .map(ChatMessageEntity::getId)
                .toList();

        Map<UUID, List<ChatAttachmentResponse>> attachmentsByMessageId =
                chatAttachmentRepository.findByMessageIds(messageIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                attachment -> attachment.getMessage().getId(),
                                Collectors.mapping(this::toAttachmentResponse, Collectors.toList())
                        ));

        Set<UUID> replyIds = messages.stream()
                .map(ChatMessageEntity::getReplyToMessage)
                .filter(Objects::nonNull)
                .map(ChatMessageEntity::getId)
                .collect(Collectors.toSet());
        Set<UUID> forwardIds = messages.stream()
                .map(ChatMessageEntity::getForwardedFromMessage)
                .filter(Objects::nonNull)
                .map(ChatMessageEntity::getId)
                .collect(Collectors.toSet());

        Set<UUID> referencedIds = new LinkedHashSet<>(replyIds);
        referencedIds.addAll(forwardIds);

        Map<UUID, ChatMessageEntity> referenced = referencedIds.isEmpty()
                ? Map.of()
                : chatMessageRepository.findAllWithUserAndChatByIdIn(referencedIds).stream()
                        .collect(Collectors.toMap(ChatMessageEntity::getId, Function.identity()));

        Map<UUID, String> replyFileNames = replyIds.isEmpty()
                ? Map.of()
                : chatAttachmentRepository.findByMessageIds(replyIds).stream()
                        .collect(Collectors.toMap(
                                attachment -> attachment.getMessage().getId(),
                                attachment -> attachment.getFile().getOriginalFileName() == null
                                        ? "" : attachment.getFile().getOriginalFileName(),
                                (first, second) -> first
                        ));

        Map<UUID, List<ChatMessageResponse.Mention>> mentionsByMessageId = loadMentions(messages, messageIds);
        Map<UUID, List<ChatMessageResponse.Reaction>> reactionsByMessageId = loadReactions(messageIds, viewerId);

        return messages.stream()
                .map(message -> toResponse(
                        message,
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of()),
                        message.getReplyToMessage() == null ? null
                                : toReplyTo(referenced.get(message.getReplyToMessage().getId()), replyFileNames),
                        message.getForwardedFromMessage() == null ? null
                                : toForwardedFrom(referenced.get(message.getForwardedFromMessage().getId())),
                        mentionsByMessageId.getOrDefault(message.getId(), List.of()),
                        reactionsByMessageId.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    /** Реакции пачкой: эмодзи в порядке первой реакции, со счётчиком и отметкой «моя». */
    private Map<UUID, List<ChatMessageResponse.Reaction>> loadReactions(Collection<UUID> messageIds, UUID viewerId) {
        List<ChatMessageReactionEntity> reactions = chatMessageReactionRepository.findByMessageIds(messageIds);

        if (reactions.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<ChatMessageResponse.Reaction>> result = new HashMap<>();

        reactions.stream()
                .collect(Collectors.groupingBy(r -> r.getMessage().getId(), java.util.LinkedHashMap::new, Collectors.toList()))
                .forEach((messageId, list) -> result.put(messageId, summarizeReactions(list, viewerId)));

        return result;
    }

    static List<ChatMessageResponse.Reaction> summarizeReactions(List<ChatMessageReactionEntity> reactions, UUID viewerId) {
        Map<String, List<ChatMessageReactionEntity>> byEmoji = reactions.stream()
                .collect(Collectors.groupingBy(ChatMessageReactionEntity::getEmoji, java.util.LinkedHashMap::new, Collectors.toList()));

        return byEmoji.entrySet().stream()
                .map(entry -> new ChatMessageResponse.Reaction(
                        entry.getKey(),
                        entry.getValue().size(),
                        viewerId != null && entry.getValue().stream()
                                .anyMatch(r -> viewerId.equals(r.getUser().getId()))
                ))
                .toList();
    }

    private Map<UUID, List<ChatMessageResponse.Mention>> loadMentions(
            List<ChatMessageEntity> messages,
            Collection<UUID> messageIds
    ) {
        List<ChatMessageMentionEntity> mentions = chatMessageMentionRepository.findByMessageIds(messageIds);

        if (mentions.isEmpty()) {
            return Map.of();
        }

        // Имя — как его видят коллеги (у не зашедшего в компанию — имя от директора).
        UUID companyId = messages.get(0).getCompany().getId();
        Set<UUID> userIds = mentions.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
        Map<UUID, CompanyMemberEntity> memberships = new HashMap<>();
        companyMemberRepository.findByCompanyIdAndUserIdIn(companyId, userIds)
                .forEach(member -> memberships.put(member.getUser().getId(), member));

        Map<UUID, List<ChatMessageResponse.Mention>> result = new HashMap<>();

        for (ChatMessageMentionEntity mention : mentions) {
            UserEntity user = mention.getUser();
            MemberProfile profile = MemberProfile.of(user, memberships.get(user.getId()));
            result.computeIfAbsent(mention.getMessage().getId(), id -> new ArrayList<>())
                    .add(new ChatMessageResponse.Mention(user.getId(), profile.fullName()));
        }

        return result;
    }

    private ChatMessageResponse.ReplyTo toReplyTo(ChatMessageEntity original, Map<UUID, String> fileNames) {
        if (original == null) {
            return null;
        }

        UserEntity author = original.getUser();
        boolean deleted = Boolean.TRUE.equals(original.getIsDeleted());
        String text = null;

        if (!deleted) {
            text = original.getContent() == null ? "" : original.getContent().strip();

            if (text.isEmpty()) {
                text = fileNames.getOrDefault(original.getId(), "");
            }

            if (text.length() > REPLY_PREVIEW_LENGTH) {
                text = text.substring(0, REPLY_PREVIEW_LENGTH).strip() + "…";
            }
        }

        return new ChatMessageResponse.ReplyTo(
                original.getId(),
                author == null ? null : author.getId(),
                author == null ? null : author.getDisplayName(),
                text,
                original.getType() == null ? null : original.getType().name(),
                deleted
        );
    }

    private ChatMessageResponse.ForwardedFrom toForwardedFrom(ChatMessageEntity original) {
        if (original == null) {
            return null;
        }

        UserEntity author = original.getUser();

        return new ChatMessageResponse.ForwardedFrom(
                original.getId(),
                author == null ? null : author.getId(),
                author == null ? null : author.getDisplayName()
        );
    }

    private ChatMessageResponse toResponse(
            ChatMessageEntity message,
            List<ChatAttachmentResponse> attachments,
            ChatMessageResponse.ReplyTo replyTo,
            ChatMessageResponse.ForwardedFrom forwardedFrom,
            List<ChatMessageResponse.Mention> mentions,
            List<ChatMessageResponse.Reaction> reactions
    ) {
        UserEntity user = message.getUser();

        return new ChatMessageResponse(
                message.getId(),
                user.getId(),
                user.getDisplayName(),
                user.getInitials(),
                message.getType().name(),
                message.getContent(),
                TimeUtils.toUtcOffset(message.getCreatedAt()),
                attachments,
                replyTo,
                forwardedFrom,
                TimeUtils.toUtcOffset(message.getEditedAt()),
                mentions,
                reactions
        );
    }

    private ChatAttachmentResponse toAttachmentResponse(ChatAttachmentEntity attachment) {
        CompanyFileEntity file = attachment.getFile();

        return new ChatAttachmentResponse(
                file.getId(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                file.getDurationMs()
        );
    }
}
