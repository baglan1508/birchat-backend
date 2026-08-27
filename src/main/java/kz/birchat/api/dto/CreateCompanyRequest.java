package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCompanyRequest(

        @NotNull(message = "ownerUserId обязателен")
        UUID ownerUserId,

        @NotBlank(message = "name обязателен")
        @Size(max = 255, message = "name не должен превышать 255 символов")
        String name,

        @Size(max = 255, message = "field не должен превышать 255 символов")
        String field,

        String logoUrl,

        String color
) {
}