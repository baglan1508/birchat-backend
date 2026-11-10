package kz.birchat.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import kz.birchat.api.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import kz.birchat.api.util.TimeUtils;
import java.util.stream.Collectors;

/**
 * Ошибки 5xx пишутся в лог с уровнем ERROR — оттуда они уходят в Sentry (sentry-logback).
 * Ошибки 4xx — ожидаемые (валидация, нет доступа), их не логируем как ошибки.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {
        if (ex.getStatus().is5xxServerError()) {
            log.error(
                    "API error {} {} on {} {}",
                    ex.getStatus().value(),
                    ex.getCode(),
                    request.getMethod(),
                    request.getRequestURI(),
                    ex
            );
        }

        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                ex.getStatus().value(),
                ex.getStatus().name(),
                ex.getCode().name(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ApiErrorCode.VALIDATION.name(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParamException(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ApiErrorCode.VALIDATION.name(),
                "Параметр " + ex.getParameterName() + " обязателен",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ApiErrorCode.VALIDATION.name(),
                "Некорректный параметр " + ex.getName(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ApiErrorCode.BAD_REQUEST.name(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled error on {} {}",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        ErrorResponse response = new ErrorResponse(
                TimeUtils.utcOffsetNow(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                ApiErrorCode.INTERNAL_ERROR.name(),
                "Внутренняя ошибка сервера",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}