package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SendCodeRequest(

        @NotBlank(message = "phone обязателен")
        String phone
) {
}