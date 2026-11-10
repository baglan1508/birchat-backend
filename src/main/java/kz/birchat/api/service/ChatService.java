package kz.birchat.api.service;

import kz.birchat.api.dto.ChatAttachmentResponse;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.ChatReadStateResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
import kz.birchat.api.dto.CreateFileMessageRequest;
import kz.birchat.api.dto.MarkChatReadRequest;
import kz.birchat.api.entity.ChatAttachmentEntity;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.ChatReadStateEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ---- общий чат: старые эндпоинты /chats/general/* ----

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(
            UUID companyId,
            UUID userId,
            UUID after,
            UUID before,
            Integer limit
    ) {
        UUID chatId = chatAccessService.requireGeneralChat(companyId).getId();
        return getMessages(companyId, chatId, userId, after, before, limit);
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
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);

        if (after != null && before != null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Нельзя одновременно использовать after и before"
            );
        }

        int safeLimit = normalizeLimit(limit);
        List<ChatMessageEntity> messages;

        if (after != null) {
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

        return toResponses(messages);
    }

    @Transactional
    public ChatMessageResponse createMessage(
            UUID companyId,
            UUID chatId,
            UUID userId,
            CreateChatMessageRequest request
    ) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        UserEntity user = findUser(userId);
        LocalDateTime now = TimeUtils.utcNow();

        ChatMessageEntity message = newMessage(chat, user, ChatMessageType.TEXT, request.text(), now);

        return toResponse(chatMessageRepository.save(message), List.of());
    }

    @Transactional
    public ChatMessageResponse createFileMessage(
            UUID companyId,
            UUID chatId,
            UUID userId,
            CreateFileMessageRequest request
    ) {
        ChatEntity chat = chatAccessService.requireReadableChat(companyId, chatId, userId);
        UserEntity user = findUser(userId);

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

        ChatMessageEntity savedMessage = chatMessageRepository.save(
                newMessage(chat, user, resolveMessageType(file.getContentType()), text, now)
        );

        ChatAttachmentEntity attachment = new ChatAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setCompany(chat.getCompany());
        attachment.setChat(chat);
        attachment.setMessage(savedMessage);
        attachment.setFile(file);
        attachment.setCreatedAt(now);

        ChatAttachmentEntity savedAttachment = chatAttachmentRepository.save(attachment);

        return toResponse(savedMessage, List.of(toAttachmentResponse(savedAttachment)));
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
     * при отправке в группу становится файлом этой группы, чтобы не светиться в общем списке.
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

        boolean ownFreshUpload = ChatAccessService.isGroup(chat)
                && file.getUploadedBy() != null
                && userId.equals(file.getUploadedBy().getId())
                && !chatAttachmentRepository.existsByFileId(file.getId());

        if (ownFreshUpload) {
            file.setChat(chat);
            file.setUpdatedAt(TimeUtils.utcNow());
            companyFileRepository.save(file);
        }
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

    private List<ChatMessageResponse> toResponses(List<ChatMessageEntity> messages) {
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

        return messages.stream()
                .map(message -> toResponse(
                        message,
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    private ChatMessageResponse toResponse(
            ChatMessageEntity message,
            List<ChatAttachmentResponse> attachments
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
                attachments
        );
    }

    private ChatAttachmentResponse toAttachmentResponse(ChatAttachmentEntity attachment) {
        CompanyFileEntity file = attachment.getFile();

        return new ChatAttachmentResponse(
                file.getId(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize()
        );
    }
}
