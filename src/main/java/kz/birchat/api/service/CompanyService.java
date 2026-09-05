package kz.birchat.api.service;

import kz.birchat.api.dto.CompanyHomeResponse;
import kz.birchat.api.dto.CompanyResponse;
import kz.birchat.api.dto.CreateCompanyRequest;
import kz.birchat.api.entity.*;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.*;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public List<CompanyResponse> getMyCompanies(UUID userId) {
        List<CompanyMemberEntity> memberships =
                companyMemberRepository.findByUserIdAndStatus(userId, "ACTIVE");

        return memberships.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyHomeResponse getCompanyHome(UUID companyId, UUID userId) {
        CompanyMemberEntity member = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в этой компании"
                ));

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        ChatEntity generalChat = chatRepository.findByCompanyIdAndType(companyId, "GENERAL")
                .orElseThrow(() -> new IllegalArgumentException("Общий чат компании не найден"));

        Long messagesCount = chatMessageRepository.countGeneralChatMessages(companyId);

        List<ChatMessageEntity> lastMessages = chatMessageRepository.findLastGeneralChatMessage(
                companyId,
                PageRequest.of(0, 1)
        );

        String lastMessage = null;
        OffsetDateTime lastMessageAt = null;

        if (!lastMessages.isEmpty()) {
            ChatMessageEntity message = lastMessages.get(0);
            lastMessage = message.getContent();
            lastMessageAt = TimeUtils.toUtcOffset(message.getCreatedAt());
        }

        boolean aiDirectorAvailable = "DIRECTOR".equals(member.getRole().getCode());

        return new CompanyHomeResponse(
                new CompanyHomeResponse.CompanyInfo(
                        company.getId(),
                        company.getName(),
                        company.getField(),
                        company.getLogoUrl(),
                        company.getInitial(),
                        company.getColorHex()
                ),
                new CompanyHomeResponse.GeneralChatInfo(
                        generalChat.getId(),
                        generalChat.getName(),
                        messagesCount,
                        lastMessage,
                        lastMessageAt
                ),
                new CompanyHomeResponse.AiDirectorInfo(
                        aiDirectorAvailable,
                        "AI Director"
                )
        );
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        UserEntity owner = userRepository.findById(request.ownerUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не найден: " + request.ownerUserId()
                ));

        RoleEntity directorRole = roleRepository.findByCode("DIRECTOR")
                .orElseThrow(() -> new IllegalArgumentException("Роль DIRECTOR не найдена"));

        LocalDateTime now = TimeUtils.utcNow();

        CompanyEntity company = new CompanyEntity();
        company.setId(UUID.randomUUID());
        company.setName(request.name());
        company.setField(request.field());
        company.setLogoUrl(request.logoUrl());
        company.setInitial(buildInitial(request.name()));
        company.setColorHex(request.color() != null ? request.color() : "#2563EB");
        company.setOwner(owner);
        company.setStatus("ACTIVE");
        company.setCreatedAt(now);
        company.setUpdatedAt(now);

        CompanyEntity savedCompany = companyRepository.save(company);

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setId(UUID.randomUUID());
        member.setCompany(savedCompany);
        member.setUser(owner);
        member.setRole(directorRole);
        member.setPosition("Директор");
        member.setStatus("ACTIVE");
        member.setJoinedAt(now);

        companyMemberRepository.save(member);

        ChatEntity generalChat = new ChatEntity();
        generalChat.setId(UUID.randomUUID());
        generalChat.setCompany(savedCompany);
        generalChat.setName("Общий чат компании");
        generalChat.setType("GENERAL");
        generalChat.setCreatedAt(now);

        chatRepository.save(generalChat);

        return new CompanyResponse(
                savedCompany.getId(),
                savedCompany.getName(),
                savedCompany.getField(),
                savedCompany.getLogoUrl(),
                savedCompany.getInitial(),
                savedCompany.getColorHex(),
                1,
                directorRole.getCode(),
                directorRole.getName(),
                "Директор"
        );
    }

    private CompanyResponse toResponse(CompanyMemberEntity member) {
        CompanyEntity company = member.getCompany();

        int employeesCount = (int) companyMemberRepository.countByCompanyIdAndStatus(
                company.getId(),
                "ACTIVE"
        );

        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getField(),
                company.getLogoUrl(),
                company.getInitial(),
                company.getColorHex(),
                employeesCount,
                member.getRole().getCode(),
                member.getRole().getName(),
                member.getPosition()
        );
    }

    private String buildInitial(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "B";
        }

        return companyName.trim().substring(0, 1).toUpperCase();
    }
}