package kz.birchat.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Проверяет заголовок "Authorization: Bearer <token>" для защищённых эндпоинтов
 * и кладёт id пользователя в атрибут запроса.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = AuthInterceptor.class.getName() + ".userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw ApiException.unauthorized(
                    ApiErrorCode.UNAUTHORIZED,
                    "Требуется авторизация"
            );
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        UUID userId;

        try {
            userId = jwtService.parseAccessToken(token);
        } catch (JwtService.InvalidTokenException ex) {
            throw ApiException.unauthorized(
                    ex.isExpired() ? ApiErrorCode.TOKEN_EXPIRED : ApiErrorCode.UNAUTHORIZED,
                    ex.getMessage()
            );
        }

        boolean activeUser = userRepository.findById(userId)
                .map(user -> !Boolean.FALSE.equals(user.getIsActive()))
                .orElse(false);

        if (!activeUser) {
            throw ApiException.unauthorized(
                    ApiErrorCode.UNAUTHORIZED,
                    "Пользователь не найден или удалён"
            );
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId);

        return true;
    }
}
