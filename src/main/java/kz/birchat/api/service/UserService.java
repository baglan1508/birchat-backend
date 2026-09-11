package kz.birchat.api.service;

import kz.birchat.api.dto.UpdateUserRequest;
import kz.birchat.api.dto.UserResponse;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.RoleRepository;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String ROLE_DIRECTOR = "DIRECTOR";

    private final UserRepository userRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        UserEntity user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateUserRequest request) {
        UserEntity user = findUser(userId);

        String fullName = request.fullName().trim();

        user.setFullName(fullName);
        user.setDisplayName(buildDisplayName(fullName));
        user.setInitials(buildInitials(fullName));
        user.setUpdatedAt(TimeUtils.utcNow());

        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional
    public void deleteMe(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            return;
        }

        List<CompanyMemberEntity> memberships =
                companyMemberRepository.findByUserIdAndStatus(userId, STATUS_ACTIVE);

        RoleEntity directorRole = roleRepository.findByCode(ROLE_DIRECTOR)
                .orElseThrow(() -> ApiException.badRequest(
                        ApiErrorCode.ROLE_NOT_FOUND,
                        "Роль DIRECTOR не найдена"
                ));

        LocalDateTime now = TimeUtils.utcNow();

        for (CompanyMemberEntity membership : memberships) {
            UUID companyId = membership.getCompany().getId();

            boolean currentUserIsDirector = isDirector(membership);

            if (currentUserIsDirector) {
                List<CompanyMemberEntity> activeMembers =
                        companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(
                                companyId,
                                STATUS_ACTIVE
                        );

                long activeDirectors = activeMembers.stream()
                        .filter(this::isDirector)
                        .count();

                if (activeDirectors == 1) {
                    activeMembers.stream()
                            .filter(member -> !userId.equals(member.getUser().getId()))
                            .findFirst()
                            .ifPresent(candidate -> {
                                candidate.setRole(directorRole);
                                companyMemberRepository.save(candidate);
                            });
                }
            }

            membership.setStatus(STATUS_INACTIVE);
            companyMemberRepository.save(membership);
        }

        user.setIsActive(false);
        user.setPhone(buildDeletedPhone(user.getId()));
        user.setFullName("Удалённый пользователь");
        user.setDisplayName("Удалённый пользователь");
        user.setInitials("УП");
        user.setAvatarUrl(null);
        user.setUpdatedAt(now);

        userRepository.save(user);
    }

    private boolean isDirector(CompanyMemberEntity member) {
        return member.getRole() != null
                && ROLE_DIRECTOR.equals(member.getRole().getCode());
    }

    private UserEntity findUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId обязателен");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Пользователь не найден"
                ));
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                user.getAvatarUrl(),
                user.getIsActive()
        );
    }

    private String buildDisplayName(String fullName) {
        return Arrays.stream(fullName.trim().split("\\s+"))
                .findFirst()
                .orElse(fullName);
    }

    private String buildInitials(String fullName) {
        return Arrays.stream(fullName.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }

    private String buildDeletedPhone(UUID userId) {
        String shortId = userId.toString()
                .replace("-", "")
                .substring(0, 12);

        return "deleted:" + shortId;
    }
}