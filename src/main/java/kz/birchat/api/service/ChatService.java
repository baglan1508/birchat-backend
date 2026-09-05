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

import java.time.LocalDateTime;
import kz.birchat.api.util.TimeUtils;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final CompanyRepository companyRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final CompanyMemberRepository companyMemberRepository;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(UUID companyId) {
        return chatMessageRepository.findGeneralChatMessages(companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGeneralChatMessages(
            UUID companyId,
            UUID userId,
            UUID after,
            Integer limit
    ) {
        checkActiveMember(companyId, userId);

        if (after == null && limit == null) {
            return chatMessageRepository.findGeneralChatMessages(companyId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        int safeLimit = normalizeLimit(limit);

        LocalDateTime afterCreatedAt = null;

        if (after != null) {
            ChatMessageEntity afterMessage = chatMessageRepository.findById(after)
                    .filter(message -> companyId.equals(message.getCompany().getId()))
                    .filter(message -> "GENERAL".equals(message.getChat().getType()))
                    .orElseThrow(() -> ApiException.notFound(
                            ApiErrorCode.MESSAGE_NOT_FOUND,
                            "Сообщение after не найдено"
                    ));

            afterCreatedAt = afterMessage.getCreatedAt();
        }

        List<ChatMessageEntity> messages;

        if (afterCreatedAt == null) {
            messages = chatMessageRepository.findGeneralChatMessagesPaged(
                    companyId,
                    PageRequest.of(0, safeLimit)
            );
        } else {
            messages = chatMessageRepository.findGeneralChatMessagesAfter(
                    companyId,
                    afterCreatedAt,
                    PageRequest.of(0, safeLimit)
            );
        }

        return messages.stream()
                .map(this::toResponse)
                .toList();
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

        return toResponse(savedMessage);
    }

    private ChatMessageResponse toResponse(ChatMessageEntity message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getUser().getId(),
                message.getUser().getDisplayName(),
                message.getUser().getInitials(),
                message.getType().name(),
                message.getContent(),
                TimeUtils.toUtcOffset(message.getCreatedAt())
        );
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
}