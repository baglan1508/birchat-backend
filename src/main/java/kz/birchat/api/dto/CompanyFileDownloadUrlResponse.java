package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyFileDownloadUrlResponse(
        UUID fileId,
        String fileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        Integer expiresInSeconds,
        OffsetDateTime expiresAt
) {
}