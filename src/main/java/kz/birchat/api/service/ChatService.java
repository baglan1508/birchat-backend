package kz.birchat.api.service;

import kz.birchat.api.repository.*;
import org.springframework.transaction.annotation.Transactional;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import kz.birchat.api.dto.ChatReadStateResponse;
import kz.birchat.api.dto.MarkChatReadRequest;
import kz.birchat.api.entity.ChatReadStateEntity;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.dto.ChatAttachmentResponse;
import kz.birchat.api.dto.CreateFileMessageRequest;
import kz.birchat.api.entity.ChatAttachmentEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.repository.ChatAttachmentRepository;

import java.time.LocalDateTime;
import kz.birchat.api.util.TimeUtils;
import java.util.List;
import java.util.UUID;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final CompanyRepository companyRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final CompanyFileRepository companyFileRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(
            UUID companyId,
            UUID userId,
            UUID after,
            UUID before,
            Integer limit
    ) {
        checkActiveMember(companyId, userId);

        if (after != null && before != null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Нельзя одновременно использовать after и before"
            );
        }

        ChatEntity generalChat = findGeneralChat(companyId);
        int safeLimit = normalizeLimit(limit);

        List<ChatMessageEntity> messages;

        if (after != null) {
            ChatMessageEntity afterMessage = findMessageCursor(companyId, generalChat.getId(), after, "after");

            messages = chatMessageRepository.findGeneralChatMessagesAfter(
                    companyId,
                    generalChat.getId(),
                    afterMessage.getCreatedAt(),
                    afterMessage.getId(),
                    PageRequest.of(0, safeLimit)
            );
        } else if (before != null) {
            ChatMessageEntity beforeMessage = findMessageCursor(companyId, generalChat.getId(), before, "before");

            messages = chatMessageRepository.findGeneralChatMessagesBefore(
                    companyId,
                    generalChat.getId(),
                    beforeMessage.getCreatedAt(),
                    beforeMessage.getId(),
                    PageRequest.of(0, safeLimit)
            );

            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository.findLatestGeneralChatMessages(
                    companyId,
                    generalChat.getId(),
                    PageRequest.of(0, safeLimit)
            );

            Collections.reverse(messages);
        }

        return toResponses(messages);
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

    public ChatMessageResponse createGeneralChatMessage(
            UUID companyId,
            CreateChatMessageRequest request
    ) {
        checkActiveMember(companyId, request.userId());
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Компания не найдена: " + companyId));

        ChatEntity chat = chatRepository.findByCompanyIdAndType(companyId, "GENERAL")
                .orElseThrow(() -> new IllegalArgumentException("Общий чат компании не найден"));

        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + request.userId()));

        LocalDateTime now = TimeUtils.utcNow();

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setCompany(company);
        message.setChat(chat);
        message.setUser(user);
        message.setType(ChatMessageType.TEXT);
        message.setContent(request.text());
        message.setIsDeleted(false);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);

        ChatMessageEntity savedMessage = chatMessageRepository.save(message);

        return toResponse(savedMessage, List.of());
    }

    private void checkActiveMember(UUID companyId, UUID userId) {
        if (userId == null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "userId обязателен"
            );
        }

        companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        boolean isMember = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .isPresent();

        if (!isMember) {
            throw ApiException.forbidden(
                    ApiErrorCode.NOT_A_MEMBER,
                    "Пользователь не состоит в этой компании"
            );
        }
    }
    private ChatMessageEntity findMessageCursor(UUID companyId, UUID messageId, String cursorName) {
        return chatMessageRepository.findById(messageId)
                .filter(message -> companyId.equals(message.getCompany().getId()))
                .filter(message -> "GENERAL".equals(message.getChat().getType()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.MESSAGE_NOT_FOUND,
                        "Сообщение " + cursorName + " не найдено"
                ));
    }
    @Transactional
    public ChatReadStateResponse markGeneralChatAsRead(
            UUID companyId,
            UUID userId,
            MarkChatReadRequest request
    ) {
        checkActiveMember(companyId, userId);

        ChatEntity generalChat = findGeneralChat(companyId);

        ChatMessageEntity message = findMessageCursor(
                companyId,
                generalChat.getId(),
                request.messageId(),
                "messageId"
        );

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        ChatReadStateEntity state = chatReadStateRepository
                .findState(companyId, generalChat.getId(), userId)
                .orElseGet(() -> {
                    ChatReadStateEntity newState = new ChatReadStateEntity();
                    newState.setId(UUID.randomUUID());
                    newState.setCompany(company);
                    newState.setChat(generalChat);
                    newState.setUser(user);
                    return newState;
                });

        if (shouldUpdateReadState(state, message)) {
            state.setLastReadMessage(message);
            state.setLastReadMessageCreatedAt(message.getCreatedAt());
        }

        state.setUpdatedAt(TimeUtils.utcNow());

        ChatReadStateEntity saved = chatReadStateRepository.save(state);

        Long unreadCount = calculateUnreadCount(
                companyId,
                generalChat.getId(),
                userId,
                saved
        );

        return new ChatReadStateResponse(
                generalChat.getId(),
                userId,
                saved.getLastReadMessage() != null ? saved.getLastReadMessage().getId() : null,
                TimeUtils.toUtcOffset(saved.getLastReadMessageCreatedAt()),
                unreadCount
        );
    }
    private ChatEntity findGeneralChat(UUID companyId) {
        return chatRepository.findByCompanyIdAndType(companyId, "GENERAL")
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.CHAT_NOT_FOUND,
                        "Общий чат не найден"
                ));
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

    @Transactional
    public ChatMessageResponse createGeneralChatFileMessage(
            UUID companyId,
            CreateFileMessageRequest request
    ) {
        checkActiveMember(companyId, request.userId());

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        ChatEntity chat = findGeneralChat(companyId);

        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));

        CompanyFileEntity file = companyFileRepository.findById(request.fileId())
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл не найден"
                ));

        LocalDateTime now = TimeUtils.utcNow();

        String text = request.text() == null
                ? ""
                : request.text().trim();

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setCompany(company);
        message.setChat(chat);
        message.setUser(user);
        message.setType(resolveMessageType(file.getContentType()));
        message.setContent(text);
        message.setIsDeleted(false);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);

        ChatMessageEntity savedMessage = chatMessageRepository.save(message);

        ChatAttachmentEntity attachment = new ChatAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setCompany(company);
        attachment.setChat(chat);
        attachment.setMessage(savedMessage);
        attachment.setFile(file);
        attachment.setCreatedAt(now);

        ChatAttachmentEntity savedAttachment = chatAttachmentRepository.save(attachment);

        return toResponse(
                savedMessage,
                List.of(toAttachmentResponse(savedAttachment))
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