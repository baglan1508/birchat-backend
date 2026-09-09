package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID userId,
        String authorName,
        String authorInitials,
        String type,
        String text,
        OffsetDateTime createdAt,
        List<ChatAttachmentResponse> attachments
) {
}