package kz.birchat.api.service;

import kz.birchat.api.dto.CompanyFileResponse;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyFileService {

    private final CompanyFileRepository companyFileRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService supabaseStorageService;

    @Transactional(readOnly = true)
    public List<CompanyFileResponse> getCompanyFiles(UUID companyId, UUID userId, Integer limit) {
        checkActiveMember(companyId, userId);

        int safeLimit = normalizeLimit(limit);

        return companyFileRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyFileResponse getCompanyFile(UUID companyId, UUID fileId, UUID userId) {
        checkActiveMember(companyId, userId);

        CompanyFileEntity file = companyFileRepository.findById(fileId)
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл не найден"
                ));

        return toResponse(file);
    }

    private void checkActiveMember(UUID companyId, UUID userId) {
        if (userId == null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "userId обязателен"
            );
        }

        companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в этой компании"
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
                TimeUtils.toUtcOffset(file.getCreatedAt())
        );
    }
    @Transactional
    public CompanyFileResponse uploadFile(UUID companyId, UUID userId, MultipartFile multipartFile) {
        checkActiveMember(companyId, userId);

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
        entity.setFileName(storedFile.fileName());
        entity.setOriginalFileName(storedFile.originalFileName());
        entity.setContentType(storedFile.contentType());
        entity.setFileSize(storedFile.fileSize());
        entity.setStorageKey(storedFile.storageKey());
        entity.setFileUrl(storedFile.fileUrl());
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
        checkActiveMember(companyId, userId);

        CompanyFileEntity file = companyFileRepository.findById(fileId)
                .filter(item -> companyId.equals(item.getCompany().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.FILE_NOT_FOUND,
                        "Файл не найден"
                ));

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
}