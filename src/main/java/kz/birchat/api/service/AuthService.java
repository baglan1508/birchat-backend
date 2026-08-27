package kz.birchat.api.service;

import kz.birchat.api.dto.AuthResponse;
import kz.birchat.api.dto.SendCodeRequest;
import kz.birchat.api.dto.SendCodeResponse;
import kz.birchat.api.dto.VerifyCodeRequest;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MOCK_CODE = "1111";

    private final UserRepository userRepository;

    public SendCodeResponse sendCode(SendCodeRequest request) {
        return new SendCodeResponse(
                "Код подтверждения отправлен. Для теста используйте код 1111",
                MOCK_CODE
        );
    }

    @Transactional
    public AuthResponse verifyCode(VerifyCodeRequest request) {
        if (!MOCK_CODE.equals(request.code())) {
            throw new IllegalArgumentException("Неверный код подтверждения");
        }

        UserEntity user = userRepository.findByPhone(request.phone())
                .orElseGet(() -> createUser(request.phone()));

        return new AuthResponse(
                user.getId(),
                user.getPhone(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                "mock-access-token-" + user.getId()
        );
    }

    private UserEntity createUser(String phone) {
        LocalDateTime now = LocalDateTime.now();

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhone(phone);
        user.setFullName("Новый пользователь");
        user.setDisplayName("Пользователь");
        user.setInitials("П");
        user.setAvatarUrl(null);
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }
}