package kz.birchat.api.dto;

public record StoredFileInfo(
        String storageKey,
        String fileUrl,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSize
) {
}