package kz.birchat.api.service;

import kz.birchat.api.dto.AuthResponse;
import kz.birchat.api.dto.TokenPairResponse;
import kz.birchat.api.dto.SendCodeRequest;
import kz.birchat.api.dto.SendCodeResponse;
import kz.birchat.api.dto.VerifyCodeRequest;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.repository.UserRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final SmsCodeService smsCodeService;
    private final JwtTokenService jwtTokenService;

    public SendCodeResponse sendCode(SendCodeRequest request) {
        smsCodeService.sendCode(request.phone());

        return new SendCodeResponse(
                "Код подтверждения отправлен"
        );
    }

    /**
     * Без внешней транзакции: неудачные попытки ввода кода должны сохраняться
     * (SmsCodeService.verifyCode помечен noRollbackFor = ApiException).
     */
    public AuthResponse verifyCode(VerifyCodeRequest request) {
        String phone = smsCodeService.verifyCode(
                request.phone(),
                request.code()
        );

        UserEntity user = userRepository.findByPhone(phone)
                .orElseGet(() -> createUser(phone));

        TokenPairResponse tokens = jwtTokenService.issue(user);

        return new AuthResponse(
                user.getId(),
                user.getPhone(),
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                tokens.accessToken(),
                TimeUtils.utcOffsetNow().plusSeconds(tokens.expiresIn()),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresIn()
        );
    }

    private UserEntity createUser(String phone) {
        LocalDateTime now = TimeUtils.utcNow();

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