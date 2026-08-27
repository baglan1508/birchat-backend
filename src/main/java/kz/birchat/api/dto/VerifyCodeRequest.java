package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyCodeRequest(

        @NotBlank(message = "phone обязателен")
        String phone,

        @NotBlank(message = "code обязателен")
        String code
) {
}