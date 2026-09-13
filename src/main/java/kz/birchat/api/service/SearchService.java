package kz.birchat.api.service;

import kz.birchat.api.dto.SearchResultResponse;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final CompanyFileRepository companyFileRepository;
    private final CompanyMemberRepository companyMemberRepository;

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(
            UUID companyId,
            UUID userId,
            String query,
            Integer limit
    ) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = normalizeLimit(limit);

        checkActiveMember(companyId, userId);

        PageRequest pageRequest = PageRequest.of(0, safeLimit);

        List<SearchResultResponse> messageResults = chatMessageRepository
                .searchMessages(companyId, normalizedQuery, pageRequest)
                .stream()
                .map(this::toMessageResult)
                .toList();

        List<SearchResultResponse> fileResults = companyFileRepository
                .searchFiles(companyId, normalizedQuery, pageRequest)
                .stream()
                .map(this::toFileResult)
                .toList();

        return Stream.concat(messageResults.stream(), fileResults.stream())
                .sorted(Comparator.comparing(SearchResultResponse::createdAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    private void checkActiveMember(UUID companyId, UUID userId) {
        boolean activeMember = companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId,
                userId,
                STATUS_ACTIVE
        );

        if (!activeMember) {
            throw ApiException.forbidden(
                    ApiErrorCode.NOT_A_MEMBER,
                    "Пользователь не состоит в компании"
            );
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "query обязателен"
            );
        }

        String value = query.trim();

        if (value.length() < 2) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "query должен содержать минимум 2 символа"
            );
        }

        return value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit < 1) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "limit должен быть больше 0"
            );
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private SearchResultResponse toMessageResult(ChatMessageEntity message) {
        String authorName = message.getUser().getDisplayName();

        if (authorName == null || authorName.isBlank()) {
            authorName = message.getUser().getFullName();
        }

        return new SearchResultResponse(
                "MESSAGE",
                message.getId(),
                "Сообщение от " + authorName,
                message.getContent(),
                TimeUtils.toUtcOffset(message.getCreatedAt()),
                message.getId(),
                null
        );
    }

    private SearchResultResponse toFileResult(CompanyFileEntity file) {
        return new SearchResultResponse(
                "FILE",
                file.getId(),
                file.getOriginalFileName(),
                buildFileDescription(file),
                TimeUtils.toUtcOffset(file.getCreatedAt()),
                null,
                file.getId()
        );
    }

    private String buildFileDescription(CompanyFileEntity file) {
        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = "unknown";
        }

        return contentType + " · " + formatFileSize(file.getFileSize());
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) {
            return "0 B";
        }

        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }

        double mb = kb / 1024.0;

        return String.format("%.1f MB", mb);
    }
}