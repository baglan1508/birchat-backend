package kz.birchat.api.dto;

import java.util.UUID;

public record ChatAttachmentResponse(
        UUID fileId,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSize
) {
}