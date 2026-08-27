package kz.birchat.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyHomeResponse(
        CompanyInfo company,
        GeneralChatInfo generalChat,
        AiDirectorInfo aiDirector
) {
    public record CompanyInfo(
            UUID id,
            String name,
            String field,
            String logoUrl,
            String initial,
            String color
    ) {
    }

    public record GeneralChatInfo(
            UUID chatId,
            String name,
            Long messagesCount,
            String lastMessage,
            LocalDateTime lastMessageAt
    ) {
    }

    public record AiDirectorInfo(
            Boolean available,
            String label
    ) {
    }
}