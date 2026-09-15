package kz.birchat.api.dto;

import java.util.List;
import java.util.UUID;

public record AiHistoryResponse(
        UUID threadId,
        List<AiHistoryMessageResponse> messages
) {
}