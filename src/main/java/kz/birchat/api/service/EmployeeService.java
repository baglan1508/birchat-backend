package kz.birchat.api.service;

import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.RoleRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyMemberRepository companyMemberRepository;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getEmployees(UUID companyId) {
        return companyMemberRepository
                .findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE")
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

        if (!"DIRECTOR".equals(actorRole) && !"ADMIN".equals(actorRole)) {
            throw new IllegalArgumentException("Недостаточно прав для добавления сотрудника");
        }

        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Компания не найдена: " + companyId));

        RoleEntity role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new IllegalArgumentException("Роль не найдена: " + request.roleCode()));

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

        if (companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, user.getId(), "ACTIVE")) {
            throw new IllegalArgumentException("Пользователь уже состоит в этой компании");
        }

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setId(UUID.randomUUID());
        member.setCompany(company);
        member.setUser(user);
        member.setRole(role);
        member.setPosition(request.position());
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());

        CompanyMemberEntity savedMember = companyMemberRepository.save(member);

        return toResponse(savedMember);
    }

    private UserEntity createUser(String phone, String fullName) {
        LocalDateTime now = LocalDateTime.now();

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

        return new EmployeeResponse(
                member.getId(),
                user.getId(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                user.getPhone(),
                user.getAvatarUrl(),
                role.getCode(),
                role.getName(),
                member.getPosition(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }

    private String buildDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Пользователь";
        }

        return fullName.trim().split("\\s+")[0];
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "П";
        }

        String[] parts = fullName.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
    private boolean hasPlaceholderName(UserEntity user) {
        return "Новый пользователь".equals(user.getFullName())
                || "Пользователь".equals(user.getDisplayName())
                || "П".equals(user.getInitials());
    }
}