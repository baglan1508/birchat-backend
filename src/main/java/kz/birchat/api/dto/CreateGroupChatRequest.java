package kz.birchat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupChatRequest(

        @NotBlank(message = "name обязателен")
        @Size(max = 100, message = "name не должен превышать 100 символов")
        String name,

        @Size(max = 200, message = "Не больше 200 участников за раз")
        List<UUID> memberUserIds
) {
}
