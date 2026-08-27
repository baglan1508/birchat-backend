package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(

        @NotBlank(message = "phone обязателен")
        String phone,

        @NotBlank(message = "fullName обязателен")
        @Size(max = 255, message = "fullName не должен превышать 255 символов")
        String fullName,

        @NotBlank(message = "roleCode обязателен")
        String roleCode,

        @Size(max = 255, message = "position не должен превышать 255 символов")
        String position
) {
}