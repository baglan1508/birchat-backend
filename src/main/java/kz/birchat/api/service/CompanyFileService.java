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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyFileService {

    private final CompanyFileRepository companyFileRepository;
    private final CompanyMemberRepository companyMemberRepository;

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
}