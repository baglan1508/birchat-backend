package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.dto.CreateUploadUrlRequest;
import kz.birchat.api.dto.DownloadUrlItem;
import kz.birchat.api.dto.DownloadUrlsRequest;
import kz.birchat.api.dto.UploadUrlResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kz.birchat.api.dto.StoredFileInfo;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.UserRepository;
import org.springframework.web.multipart.MultipartFile;
import kz.birchat.api.dto.CompanyFileDownloadUrlResponse;
import kz.birchat.api.dto.SignedUrlResponse;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyFileService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";

    /** Максимальный размер файла (как у загрузки через сервер). */
    public static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;

    /**
     * Разрешённые типы: фото, голосовые и видео, документы офиса, PDF, текст, архивы.
     * Исполняемые файлы и прочее — запрещены.
     */
    private static final Set<String> ALLOWED_EXACT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/rtf",
            "application/zip",
            "application/x-zip-compressed",
            "text/plain",
            "text/csv"
    );

    private static final List<String> ALLOWED_TYPE_PREFIXES = List.of(
            "image/",
            "audio/",
            "video/",
            "application/vnd.openxmlformats-officedocument."
    );

    private final CompanyFileRepository companyFileRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final ChatAccessService chatAccessService;

    @Transactional(readOnly = true)
    public List<CompanyFileResponse> getCompanyFiles(UUID companyId, UUID userId, Integer limit) {
        CompanyMemberEntity member = checkActiveMember(companyId, userId);

        int safeLimit = normalizeLimit(limit);

        return companyFileRepository
                .findAccessibleFiles(
                        companyId,
                        userId,
                        CurrentUserService.isDirector(member),
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyFileResponse getCompanyFile(UUID companyId, UUID fileId, UUID userId) {
        CompanyMemberEntity member = checkActiveMember(companyId, userId);

        return toResponse(findAccessibleFile(companyId, fileId, userId, member));
    }

    private CompanyMemberEntity checkActiveMember(UUID companyId, UUID userId) {
        if (userId == null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "userId обязателен"
            );
        }

        return companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в этой компании"
                ));
    }

    /**
     * Файл чужой группы отдаём как «не найден», чтобы не раскрывать его существование.
     */
    private CompanyFileEntity findAccessibleFile(
            UUID companyId,
            UUID fileId,
            UUID userId,
            CompanyMemberEntity member
    ) {
        return companyFileRepository.findById(fileId)
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .filter(item -> STATUS_READY.equals(item.getStatus()))
                .filter(item -> chatAccessService.canAccessFile(item, member, userId))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл не найден"
                ));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }

        if (limit < 1) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "limit должен быть больше 0"
            );
        }

        return Math.min(limit, 100);
    }

    private CompanyFileResponse toResponse(CompanyFileEntity file) {
        return new CompanyFileResponse(
                file.getId(),
                file.getCompany().getId(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getDisplayName(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                file.getFileUrl(),
                TimeUtils.toUtcOffset(file.getCreatedAt()),
                file.getChat() == null ? null : file.getChat().getId()
        );
    }
    @Transactional
    public CompanyFileResponse uploadFile(
            UUID companyId,
            UUID userId,
            UUID chatId,
            MultipartFile multipartFile
    ) {
        checkActiveMember(companyId, userId);

        // Файл для группы сразу привязываем к ней: его увидят только участники группы.
        ChatEntity chat = null;

        if (chatId != null) {
            ChatEntity target = chatAccessService.requireReadableChat(companyId, chatId, userId);
            chat = ChatAccessService.isGroup(target) ? target : null;
        }

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));

        StoredFileInfo storedFile = supabaseStorageService.upload(companyId, userId, multipartFile);

        CompanyFileEntity entity = new CompanyFileEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompany(company);
        entity.setUploadedBy(user);
        entity.setChat(chat);
        entity.setStatus(STATUS_READY);
        entity.setFileName(storedFile.fileName());
        entity.setOriginalFileName(storedFile.originalFileName());
        entity.setContentType(storedFile.contentType());
        entity.setFileSize(storedFile.fileSize());
        entity.setStorageKey(storedFile.storageKey());
        entity.setFileUrl(storedFile.fileUrl());
        FileAnalysisService.markForAnalysisIfSupported(entity);
        entity.setCreatedAt(TimeUtils.utcNow());
        entity.setUpdatedAt(TimeUtils.utcNow());

        CompanyFileEntity saved = companyFileRepository.save(entity);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CompanyFileDownloadUrlResponse getDownloadUrl(
            UUID companyId,
            UUID fileId,
            UUID userId,
            Integer expiresInSeconds
    ) {
        CompanyMemberEntity member = checkActiveMember(companyId, userId);

        CompanyFileEntity file = findAccessibleFile(companyId, fileId, userId, member);

        SignedUrlResponse signedUrl = supabaseStorageService.createSignedUrl(
                file.getStorageKey(),
                expiresInSeconds
        );

        OffsetDateTime expiresAt = TimeUtils.utcOffsetNow()
                .plusSeconds(signedUrl.expiresInSeconds());

        return new CompanyFileDownloadUrlResponse(
                file.getId(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                signedUrl.signedUrl(),
                signedUrl.expiresInSeconds(),
                expiresAt
        );
    }

    // ---- прямая загрузка: телефон → Supabase, сервер выдаёт ссылку и подтверждает ----

    /**
     * Шаг 1. Создаёт запись PENDING и выдаёт подписанную ссылку, по которой телефон
     * сам загрузит файл в Supabase. Файл никому не виден, пока не подтверждён.
     */
    @Transactional
    public UploadUrlResponse createUploadUrl(UUID companyId, UUID userId, CreateUploadUrlRequest request) {
        checkActiveMember(companyId, userId);

        String contentType = normalizeContentType(request.contentType());
        validateFileSize(request.fileSize());

        ChatEntity chat = null;

        if (request.chatId() != null) {
            ChatEntity target = chatAccessService.requireReadableChat(companyId, request.chatId(), userId);
            chat = ChatAccessService.isGroup(target) ? target : null;
        }

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.COMPANY_NOT_FOUND, "Компания не найдена"));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND, "Пользователь не найден"));

        String originalFileName = supabaseStorageService.normalizeFileName(request.fileName());
        String storedFileName = supabaseStorageService.buildStoredFileName(originalFileName);
        String storageKey = supabaseStorageService.buildStorageKey(companyId, storedFileName);

        String uploadUrl = supabaseStorageService.createSignedUploadUrl(storageKey);

        LocalDateTime now = TimeUtils.utcNow();

        CompanyFileEntity entity = new CompanyFileEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompany(company);
        entity.setUploadedBy(user);
        entity.setChat(chat);
        entity.setFileName(storedFileName);
        entity.setOriginalFileName(originalFileName);
        entity.setContentType(contentType);
        entity.setFileSize(request.fileSize());
        entity.setStorageKey(storageKey);
        entity.setFileUrl(supabaseStorageService.objectUrl(storageKey));
        entity.setStatus(STATUS_PENDING);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        companyFileRepository.save(entity);

        return new UploadUrlResponse(
                entity.getId(),
                uploadUrl,
                "PUT",
                Map.of(
                        "Content-Type", contentType,
                        "x-upsert", "false"
                ),
                TimeUtils.utcOffsetNow().plusSeconds(SupabaseStorageService.SIGNED_UPLOAD_URL_TTL_SECONDS),
                MAX_FILE_SIZE
        );
    }

    /**
     * Шаг 3. Телефон сообщил, что загрузил файл. Проверяем, что файл действительно лежит
     * в хранилище и не больше лимита, фиксируем настоящие размер и тип и открываем доступ.
     *
     * noRollbackFor: при слишком большом файле запись удаляется и сразу выбрасывается 400 —
     * без этого флага удаление записи откатилось бы вместе с транзакцией.
     * Остальные ApiException здесь выбрасываются до любых изменений в базе.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public CompanyFileResponse completeUpload(UUID companyId, UUID fileId, UUID userId) {
        checkActiveMember(companyId, userId);

        CompanyFileEntity file = companyFileRepository.findById(fileId)
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .filter(item -> item.getUploadedBy() != null && userId.equals(item.getUploadedBy().getId()))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.FILE_NOT_FOUND, "Файл не найден"));

        if (STATUS_READY.equals(file.getStatus())) {
            return toResponse(file);
        }

        SupabaseStorageService.StoredObjectInfo stored = supabaseStorageService
                .getObjectInfo(file.getStorageKey())
                .orElseThrow(() -> ApiException.badRequest(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл ещё не загружен в хранилище"
                ));

        if (stored.size() != null && stored.size() > MAX_FILE_SIZE) {
            supabaseStorageService.deleteObjects(List.of(file.getStorageKey()));
            companyFileRepository.delete(file);

            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Размер файла не должен превышать 20MB"
            );
        }

        if (stored.size() != null) {
            file.setFileSize(stored.size());
        }

        if (stored.contentType() != null && !stored.contentType().isBlank()) {
            file.setContentType(stored.contentType());
        }

        file.setStatus(STATUS_READY);
        FileAnalysisService.markForAnalysisIfSupported(file);
        file.setUpdatedAt(TimeUtils.utcNow());

        return toResponse(companyFileRepository.save(file));
    }

    /**
     * Ссылки на скачивание сразу для многих файлов — например, все фото на экране чата.
     * Файлы, к которым у пользователя нет доступа, просто не попадают в ответ.
     */
    @Transactional(readOnly = true)
    public List<DownloadUrlItem> getDownloadUrls(UUID companyId, UUID userId, DownloadUrlsRequest request) {
        CompanyMemberEntity member = checkActiveMember(companyId, userId);

        List<CompanyFileEntity> files = companyFileRepository.findAllById(new LinkedHashSet<>(request.fileIds()))
                .stream()
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .filter(item -> STATUS_READY.equals(item.getStatus()))
                .filter(item -> chatAccessService.canAccessFile(item, member, userId))
                .toList();

        if (files.isEmpty()) {
            return List.of();
        }

        int expiresIn = supabaseStorageService.normalizeDownloadExpiresIn(request.expiresInSeconds());

        Map<String, String> urls = supabaseStorageService.createSignedUrls(
                files.stream().map(CompanyFileEntity::getStorageKey).toList(),
                expiresIn
        );

        OffsetDateTime expiresAt = TimeUtils.utcOffsetNow().plusSeconds(expiresIn);

        return files.stream()
                .filter(file -> urls.containsKey(file.getStorageKey()))
                .map(file -> new DownloadUrlItem(
                        file.getId(),
                        file.getContentType(),
                        file.getFileSize(),
                        urls.get(file.getStorageKey()),
                        expiresAt
                ))
                .toList();
    }

    static String normalizeContentType(String rawContentType) {
        String value = rawContentType == null ? "" : rawContentType.trim().toLowerCase();

        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }

        boolean allowed = ALLOWED_EXACT_TYPES.contains(value)
                || ALLOWED_TYPE_PREFIXES.stream().anyMatch(value::startsWith);

        if (!allowed) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Тип файла не поддерживается: " + (value.isBlank() ? "не указан" : value)
            );
        }

        return value;
    }

    private void validateFileSize(Long fileSize) {
        if (fileSize == null || fileSize < 1) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "fileSize должен быть больше 0");
        }

        if (fileSize > MAX_FILE_SIZE) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION, "Размер файла не должен превышать 20MB");
        }
    }
}
