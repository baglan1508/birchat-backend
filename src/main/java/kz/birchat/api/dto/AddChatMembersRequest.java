package kz.birchat.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AddChatMembersRequest(

        @NotEmpty(message = "userIds обязателен")
        @Size(max = 200, message = "Не больше 200 участников за раз")
        List<UUID> userIds
) {
}
