package kz.birchat.api.service;

import kz.birchat.api.config.SupabaseStorageProperties;
import kz.birchat.api.dto.StoredFileInfo;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import kz.birchat.api.dto.SignedUrlResponse;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final SupabaseStorageProperties properties;

    public StoredFileInfo upload(UUID companyId, UUID userId, MultipartFile file) {
        validateConfig();
        validateFile(file);

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;

        String storageKey = "companies/%s/%s/%s"
                .formatted(companyId, LocalDate.now(), storedFileName);

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try {
            byte[] bytes = file.getBytes();

            RestClient restClient = RestClient.builder()
                    .baseUrl(removeTrailingSlash(properties.url()))
                    .build();

            restClient.post()
                    .uri("/storage/v1/object/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header("x-upsert", "false")
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();

            String fileUrl = removeTrailingSlash(properties.url())
                    + "/storage/v1/object/"
                    + properties.bucket()
                    + "/"
                    + storageKey;

            return new StoredFileInfo(
                    storageKey,
                    fileUrl,
                    storedFileName,
                    originalFileName,
                    contentType,
                    file.getSize()
            );
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest(
                    ApiErrorCode.STORAGE_ERROR,
                    "Ошибка загрузки файла в storage: " + ex.getStatusCode()
            );
        } catch (IOException ex) {
            throw ApiException.badRequest(
                    ApiErrorCode.STORAGE_ERROR,
                    "Не удалось прочитать файл"
            );
        }
    }

    private void validateConfig() {
        if (isBlank(properties.url())
                || isBlank(properties.serviceKey())
                || isBlank(properties.bucket())) {
            throw ApiException.badRequest(
                    ApiErrorCode.STORAGE_ERROR,
                    "Storage не настроен"
            );
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Файл обязателен"
            );
        }

        long maxSize = 20L * 1024L * 1024L;

        if (file.getSize() > maxSize) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Размер файла не должен превышать 20MB"
            );
        }
    }

    private String normalizeOriginalFileName(String originalFileName) {
        String value = Objects.requireNonNullElse(originalFileName, "file");

        value = value.replace("\\", "/");

        int lastSlash = value.lastIndexOf("/");
        if (lastSlash >= 0) {
            value = value.substring(lastSlash + 1);
        }

        value = value.trim();

        if (value.isBlank()) {
            return "file";
        }

        return value;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(dotIndex).toLowerCase();

        if (extension.length() > 20) {
            return "";
        }

        return extension.replaceAll("[^a-z0-9.]", "");
    }

    private String removeTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public SignedUrlResponse createSignedUrl(String storageKey, Integer expiresInSeconds) {
        validateConfig();

        if (storageKey == null || storageKey.isBlank()) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "storageKey обязателен"
            );
        }

        int safeExpiresIn = normalizeExpiresIn(expiresInSeconds);

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(removeTrailingSlash(properties.url()))
                    .build();

            Map<String, Object> response = restClient.post()
                    .uri("/storage/v1/object/sign/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", safeExpiresIn))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null) {
                throw ApiException.badRequest(
                        ApiErrorCode.STORAGE_ERROR,
                        "Storage вернул пустой ответ"
                );
            }

            Object signedUrlValue = response.get("signedURL");

            if (signedUrlValue == null) {
                signedUrlValue = response.get("signedUrl");
            }

            if (signedUrlValue == null || signedUrlValue.toString().isBlank()) {
                throw ApiException.badRequest(
                        ApiErrorCode.STORAGE_ERROR,
                        "Storage не вернул signed URL"
                );
            }

            String signedUrl = toFullStorageUrl(signedUrlValue.toString());

            return new SignedUrlResponse(
                    signedUrl,
                    safeExpiresIn
            );
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest(
                    ApiErrorCode.STORAGE_ERROR,
                    "Ошибка получения signed URL: "
                            + ex.getStatusCode()
                            + ". Body: "
                            + ex.getResponseBodyAsString()
            );
        }
    }

    private int normalizeExpiresIn(Integer expiresInSeconds) {
        if (expiresInSeconds == null) {
            return 300;
        }

        if (expiresInSeconds < 60) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "expiresInSeconds должен быть не меньше 60"
            );
        }

        return Math.min(expiresInSeconds, 3600);
    }

    private String toFullStorageUrl(String signedUrl) {
        if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
            return signedUrl;
        }

        String baseUrl = removeTrailingSlash(properties.url());

        if (signedUrl.startsWith("/storage/v1")) {
            return baseUrl + signedUrl;
        }

        if (signedUrl.startsWith("/object/")) {
            return baseUrl + "/storage/v1" + signedUrl;
        }

        if (signedUrl.startsWith("object/")) {
            return baseUrl + "/storage/v1/" + signedUrl;
        }

        return baseUrl + "/storage/v1/" + signedUrl;
    }
}