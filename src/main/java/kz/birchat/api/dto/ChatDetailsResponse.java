package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatDetailsResponse(
        UUID chatId,
        String type,
        String name,
        UUID createdBy,
        OffsetDateTime createdAt,
        Boolean canManage,
        List<ChatMemberResponse> members
) {
}
