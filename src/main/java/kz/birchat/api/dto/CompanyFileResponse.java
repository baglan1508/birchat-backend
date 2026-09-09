package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyFileResponse(
        UUID id,
        UUID companyId,
        UUID uploadedBy,
        String uploadedByName,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSize,
        String fileUrl,
        OffsetDateTime createdAt
) {
}