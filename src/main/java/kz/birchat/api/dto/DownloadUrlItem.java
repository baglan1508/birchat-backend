package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DownloadUrlItem(
        UUID fileId,
        String contentType,
        Long fileSize,
        String downloadUrl,
        OffsetDateTime expiresAt
) {
}
