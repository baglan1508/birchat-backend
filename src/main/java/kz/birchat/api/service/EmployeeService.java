package kz.birchat.api.service;

import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.RoleRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.PhoneUtils;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final ChatMemberRepository chatMemberRepository;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getEmployees(UUID companyId, UUID userId) {
        checkActiveMember(companyId, userId);

        return companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EmployeeResponse addEmployee(UUID companyId, UUID actorUserId, CreateEmployeeRequest request) {
        String phone = PhoneUtils.normalize(request.phone());
        CompanyMemberEntity actorMember = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, actorUserId, "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не состоит в компании"));

        String actorRole = actorMember.getRole().getCode();

        if (!"DIRECTOR".equals(actorRole)) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Операция доступна только директору компании"
            );
        }

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Компания не найдена: " + companyId));

        RoleEntity role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> ApiException.badRequest(
                        ApiErrorCode.ROLE_NOT_FOUND,
                        "Роль не найдена"
                ));

        if ("DIRECTOR".equals(role.getCode())) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Назначать роль DIRECTOR через добавление сотрудника запрещено"
            );
        }

        UserEntity user = userRepository.findByPhone(phone)
                .map(existingUser -> {
                    if (hasPlaceholderName(existingUser)) {
                        String fullName = request.fullName().trim();
                        existingUser.setFullName(fullName);
                        existingUser.setDisplayName(buildDisplayName(fullName));
                        existingUser.setInitials(buildInitials(fullName));
                        existingUser.setUpdatedAt(LocalDateTime.now());
                        return userRepository.save(existingUser);
                    }

                    return existingUser;
                })
                .orElseGet(() -> createUser(phone, request.fullName()));

        // Пара компания + человек уникальна: убранного раньше сотрудника возвращаем той же записью.
        CompanyMemberEntity member = companyMemberRepository
                .findByCompanyIdAndUserId(companyId, user.getId())
                .orElse(null);

        if (member != null && STATUS_ACTIVE.equals(member.getStatus())) {
            throw ApiException.conflict(
                    ApiErrorCode.ALREADY_MEMBER,
                    "Пользователь уже состоит в этой компании"
            );
        }

        if (member == null) {
            member = new CompanyMemberEntity();
            member.setId(UUID.randomUUID());
            member.setCompany(company);
            member.setUser(user);
        }

        member.setRole(role);
        member.setPosition(request.position());
        member.setStatus(STATUS_ACTIVE);
        member.setJoinedAt(TimeUtils.utcNow());
        member.setInvitedName(request.fullName().trim());
        member.setAcceptedAt(null);

        CompanyMemberEntity savedMember = companyMemberRepository.save(member);

        // Ответ одинаковый для нового и уже зарегистрированного номера: без userId и профиля.
        return toInvitedResponse(savedMember, phone);
    }

    /**
     * Убрать сотрудника из компании — только директор, себя нельзя. Человек выходит из общего чата
     * и всех групп компании; его сообщения остаются, устройства не трогаем (у него могут быть
     * другие компании).
     */
    @Transactional
    public void removeEmployee(UUID companyId, UUID actorUserId, UUID employeeUserId) {
        CompanyMemberEntity actor = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, actorUserId, STATUS_ACTIVE)
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в этой компании"
                ));

        if (!"DIRECTOR".equals(actor.getRole().getCode())) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Убрать сотрудника может только директор"
            );
        }

        if (actorUserId.equals(employeeUserId)) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Нельзя убрать из компании себя"
            );
        }

        CompanyMemberEntity member = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, employeeUserId, STATUS_ACTIVE)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Сотрудник не найден в компании"
                ));

        if ("DIRECTOR".equals(member.getRole().getCode())) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "Директора нельзя убрать из компании"
            );
        }

        member.setStatus(STATUS_REMOVED);
        companyMemberRepository.save(member);
        chatMemberRepository.deleteFromCompanyGroups(companyId, employeeUserId);
    }

    private UserEntity createUser(String phone, String fullName) {
        LocalDateTime now = TimeUtils.utcNow();

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setDisplayName(buildDisplayName(fullName));
        user.setInitials(buildInitials(fullName));
        user.setAvatarUrl(null);
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }

    private EmployeeResponse toResponse(CompanyMemberEntity member) {
        UserEntity user = member.getUser();
        RoleEntity role = member.getRole();
        MemberProfile profile = MemberProfile.of(user, member);

        return new EmployeeResponse(
                member.getId(),
                user.getId(),
                profile.fullName(),
                profile.displayName(),
                profile.initials(),
                user.getPhone(),
                profile.avatarUrl(),
                role.getCode(),
                role.getName(),
                member.getPosition(),
                member.getStatus(),
                TimeUtils.toUtcOffset(member.getJoinedAt())
        );
    }

    private EmployeeResponse toInvitedResponse(CompanyMemberEntity member, String phone) {
        RoleEntity role = member.getRole();
        String name = member.getInvitedName();

        return new EmployeeResponse(
                member.getId(),
                null,
                name,
                MemberProfile.displayName(name),
                MemberProfile.initials(name),
                phone,
                null,
                role.getCode(),
                role.getName(),
                member.getPosition(),
                member.getStatus(),
                TimeUtils.toUtcOffset(member.getJoinedAt())
        );
    }

    private String buildDisplayName(String fullName) {
        return MemberProfile.displayName(fullName);
    }

    private String buildInitials(String fullName) {
        return MemberProfile.initials(fullName);
    }
    private boolean hasPlaceholderName(UserEntity user) {
        return "Новый пользователь".equals(user.getFullName())
                || "Пользователь".equals(user.getDisplayName())
                || "П".equals(user.getInitials());
    }
    private void checkActiveMember(UUID companyId, UUID userId) {
        if (userId == null) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "userId обязателен"
            );
        }

        companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.COMPANY_NOT_FOUND,
                        "Компания не найдена"
                ));

        companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE")
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в этой компании"
                ));
    }
}