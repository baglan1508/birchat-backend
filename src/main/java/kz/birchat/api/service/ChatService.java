package kz.birchat.api.service;

import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final CompanyRepository companyRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public List<ChatMessageResponse> getGeneralChatMessages(UUID companyId) {
        return chatMessageRepository.findGeneralChatMessages(companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatMessageResponse createGeneralChatMessage(
            UUID companyId,
            CreateChatMessageRequest request
    ) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Компания не найдена: " + companyId));

        ChatEntity chat = chatRepository.findByCompanyIdAndType(companyId, "GENERAL")
                .orElseThrow(() -> new IllegalArgumentException("Общий чат компании не найден"));

        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + request.userId()));

        LocalDateTime now = LocalDateTime.now();

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
                message.getCreatedAt()
        );
    }
}