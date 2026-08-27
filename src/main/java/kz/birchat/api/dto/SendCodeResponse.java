package kz.birchat.api.dto;

public record SendCodeResponse(
        String message,
        String testCode
) {
}