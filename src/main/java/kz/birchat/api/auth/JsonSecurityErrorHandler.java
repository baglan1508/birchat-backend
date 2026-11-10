package kz.birchat.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.birchat.api.dto.ErrorResponse;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Отдаёт ошибки Spring Security в том же JSON-формате, что и GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        boolean hasBearer = request.getHeader("Authorization") != null;
        boolean expired = exception.getMessage() != null
                && exception.getMessage().toLowerCase().contains("expired");

        ApiErrorCode code = expired ? ApiErrorCode.TOKEN_EXPIRED : ApiErrorCode.UNAUTHORIZED;
        String message;

        if (expired) {
            message = "Срок действия токена истёк";
        } else if (hasBearer) {
            message = "Недействительный токен";
        } else {
            message = "Требуется авторизация";
        }

        write(request, response, HttpStatus.UNAUTHORIZED, code, message);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Доступ запрещён");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String message
    ) throws IOException {
        ErrorResponse body = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                status.value(),
                status.name(),
                code.name(),
                message,
                request.getRequestURI()
        );

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
