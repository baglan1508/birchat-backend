package kz.birchat.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Куда и как загрузить файл: method + uploadUrl + headers, тело запроса — байты файла.
 * После успешной загрузки — POST /files/{fileId}/complete.
 */
public record UploadUrlResponse(
        UUID fileId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        OffsetDateTime expiresAt,
        Long maxFileSize
) {
}
