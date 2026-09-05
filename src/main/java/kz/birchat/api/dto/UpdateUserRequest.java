package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "fullName обязателен")
        @Size(max = 255, message = "fullName не должен превышать 255 символов")
        String fullName
) {
}