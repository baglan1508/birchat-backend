package kz.birchat.api.service;

import kz.birchat.api.config.SupabaseStorageProperties;
import kz.birchat.api.dto.StoredFileInfo;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import kz.birchat.api.dto.SignedUrlResponse;
import org.springframework.core.ParameterizedTypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final SupabaseStorageProperties properties;

    public StoredFileInfo upload(UUID companyId, UUID userId, MultipartFile file) {
        validateConfig();
        validateFile(file);

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        String storedFileName = buildStoredFileName(originalFileName);
        String storageKey = buildStorageKey(companyId, storedFileName);

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
                    .headers(this::applyLegacyBearer)
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

    // ---- прямая загрузка с телефона в Supabase по подписанной ссылке ----

    /** Подписанная ссылка на загрузку в Supabase действует 2 часа (ограничение Supabase). */
    public static final long SIGNED_UPLOAD_URL_TTL_SECONDS = 2 * 60 * 60;

    public String buildStoredFileName(String originalFileName) {
        return UUID.randomUUID() + extractExtension(originalFileName);
    }

    public String buildStorageKey(UUID companyId, String storedFileName) {
        return "companies/%s/%s/%s".formatted(companyId, LocalDate.now(), storedFileName);
    }

    public String normalizeFileName(String originalFileName) {
        return normalizeOriginalFileName(originalFileName);
    }

    public String objectUrl(String storageKey) {
        return removeTrailingSlash(properties.url())
                + "/storage/v1/object/"
                + properties.bucket()
                + "/"
                + storageKey;
    }

    /**
     * Ссылка, по которой телефон сам загрузит файл в Supabase (PUT, тело — байты файла).
     * Сервер в передаче данных не участвует.
     */
    public String createSignedUploadUrl(String storageKey) {
        validateConfig();

        try {
            Map<String, Object> response = client().post()
                    .uri("/storage/v1/object/upload/sign/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            Object url = response == null ? null : response.get("url");

            if (url == null) {
                url = response == null ? null : response.get("signedUrl");
            }

            if (url == null || url.toString().isBlank()) {
                throw storageError("Storage не вернул ссылку для загрузки");
            }

            return toFullStorageUrl(url.toString());
        } catch (RestClientResponseException ex) {
            log.warn("Supabase upload sign failed. status={}", ex.getStatusCode().value());
            throw storageError("Не удалось получить ссылку для загрузки: " + ex.getStatusCode().value());
        }
    }

    /**
     * Сведения о загруженном объекте или пусто, если объекта нет.
     * Сначала GET /object/info (JSON), при неудаче — HEAD на сам объект (заголовки).
     */
    public Optional<StoredObjectInfo> getObjectInfo(String storageKey) {
        validateConfig();

        try {
            Map<String, Object> info = client().get()
                    .uri("/storage/v1/object/info/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            StoredObjectInfo parsed = parseObjectInfo(info);

            if (parsed != null) {
                return Optional.of(parsed);
            }
        } catch (RestClientResponseException ex) {
            log.debug("Supabase object info failed, trying HEAD. status={}", ex.getStatusCode().value());
        }

        try {
            ResponseEntity<Void> head = client().head()
                    .uri("/storage/v1/object/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .retrieve()
                    .toBodilessEntity();

            long size = head.getHeaders().getContentLength();
            MediaType type = head.getHeaders().getContentType();

            return Optional.of(new StoredObjectInfo(
                    size >= 0 ? size : null,
                    type == null ? null : type.toString()
            ));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                return Optional.empty();
            }

            log.warn("Supabase object HEAD failed. status={}", ex.getStatusCode().value());
            throw storageError("Не удалось проверить файл в хранилище");
        }
    }

    /**
     * Подписанные ссылки на скачивание сразу для многих файлов — одним запросом к Supabase.
     *
     * @return storageKey → полная ссылка (файлы, для которых Supabase вернул ошибку, пропускаются)
     */
    public Map<String, String> createSignedUrls(List<String> storageKeys, Integer expiresInSeconds) {
        validateConfig();

        if (storageKeys == null || storageKeys.isEmpty()) {
            return Map.of();
        }

        int safeExpiresIn = normalizeExpiresIn(expiresInSeconds);

        try {
            List<Map<String, Object>> response = client().post()
                    .uri("/storage/v1/object/sign/" + properties.bucket())
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", safeExpiresIn, "paths", storageKeys))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            Map<String, String> result = new LinkedHashMap<>();

            if (response == null) {
                return result;
            }

            for (Map<String, Object> item : response) {
                Object path = item.get("path");
                Object signed = item.get("signedURL") != null ? item.get("signedURL") : item.get("signedUrl");

                if (path != null && signed != null && item.get("error") == null) {
                    result.put(path.toString(), toFullStorageUrl(signed.toString()));
                }
            }

            return result;
        } catch (RestClientResponseException ex) {
            log.warn("Supabase batch sign failed. status={}", ex.getStatusCode().value());
            throw storageError("Не удалось получить ссылки на файлы: " + ex.getStatusCode().value());
        }
    }

    public int normalizeDownloadExpiresIn(Integer expiresInSeconds) {
        return normalizeExpiresIn(expiresInSeconds);
    }

    /** Удаляет объекты из хранилища (например, недозагруженные файлы). */
    public void deleteObjects(List<String> storageKeys) {
        validateConfig();

        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }

        try {
            client().method(HttpMethod.DELETE)
                    .uri("/storage/v1/object/" + properties.bucket())
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", storageKeys))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.warn("Supabase delete failed. status={}", ex.getStatusCode().value());
            throw storageError("Не удалось удалить файлы из хранилища");
        }
    }

    /**
     * Скачать файл из хранилища на диск сервера (для чтения PDF). Поток пишется сразу в файл —
     * файл до 20 МБ не держится целиком в памяти.
     */
    public void downloadToFile(String storageKey, Path target) {
        validateConfig();

        try {
            longClient().get()
                    .uri("/storage/v1/object/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw storageError("Не удалось скачать файл: " + response.getStatusCode().value());
                        }

                        try (InputStream body = response.getBody()) {
                            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                        }

                        return null;
                    });
        } catch (RestClientResponseException ex) {
            throw storageError("Не удалось скачать файл: " + ex.getStatusCode().value());
        }
    }

    /** Служебная загрузка (страница скана PDF для модели). Перезаписывает объект с тем же ключом. */
    public void uploadBytes(String storageKey, byte[] bytes, String contentType) {
        validateConfig();

        try {
            longClient().post()
                    .uri("/storage/v1/object/" + properties.bucket() + "/" + storageKey)
                    .header("apikey", properties.serviceKey())
                    .headers(this::applyLegacyBearer)
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header("x-upsert", "true")
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw storageError("Не удалось загрузить файл: " + ex.getStatusCode().value());
        }
    }

    @SuppressWarnings("unchecked")
    static StoredObjectInfo parseObjectInfo(Map<String, Object> info) {
        if (info == null || info.isEmpty()) {
            return null;
        }

        Map<String, Object> metadata = info.get("metadata") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        Long size = firstLong(metadata.get("size"), metadata.get("contentLength"), info.get("size"));
        String type = firstString(metadata.get("mimetype"), info.get("content_type"), info.get("contentType"));

        if (size == null && type == null) {
            return null;
        }

        return new StoredObjectInfo(size, type);
    }

    private static Long firstLong(Object... values) {
        for (Object value : values) {
            if (value instanceof Number n) {
                return n.longValue();
            }

            if (value instanceof String s && !s.isBlank()) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // следующее значение
                }
            }
        }

        return null;
    }

    private static String firstString(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }

        return null;
    }

    /** Короткие служебные запросы к Supabase (подписи, проверки, удаление) — с таймаутами. */
    private RestClient client() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(removeTrailingSlash(properties.url()))
                .build();
    }

    /** Для скачивания и загрузки файлов целиком — ответ может идти дольше 15 секунд. */
    private RestClient longClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(removeTrailingSlash(properties.url()))
                .build();
    }

    private ApiException storageError(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCode.STORAGE_ERROR, message);
    }

    public record StoredObjectInfo(Long size, String contentType) {
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
                    .headers(this::applyLegacyBearer)
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

    /**
     * Старые service_role ключи Supabase — это JWT, их нужно передавать ещё и как Bearer.
     * Новые секретные ключи (sb_secret_...) не являются JWT и передаются только в заголовке apikey.
     */
    private void applyLegacyBearer(HttpHeaders headers) {
        String key = properties.serviceKey();

        if (key != null && key.startsWith("eyJ")) {
            headers.setBearerAuth(key);
        }
    }
}
