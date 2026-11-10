package kz.birchat.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DownloadUrlsRequest(

        @NotEmpty(message = "fileIds обязателен")
        @Size(max = 100, message = "Не больше 100 файлов за раз")
        List<UUID> fileIds,

        Integer expiresInSeconds
) {
}
