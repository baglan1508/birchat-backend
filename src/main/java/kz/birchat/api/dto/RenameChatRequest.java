package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChatRequest(
        @NotBlank(message = "name обязателен")
        @Size(max = 100, message = "name не должен превышать 100 символов")
        String name
) {
}
