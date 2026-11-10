package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.dto.CompanyFileResponse;
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
}