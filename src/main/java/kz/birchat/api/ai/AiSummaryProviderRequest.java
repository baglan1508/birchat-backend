package kz.birchat.api.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сообщения для сводки. chats — чаты, из которых они взяты: у каждого номер, по которому
 * модель возвращает раздел сводки (в сообщениях он стоит в source: «#2 Группа «Склад»»).
 */
public record AiSummaryProviderRequest(
        UUID companyId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        List<AiProviderMessage> contextMessages,
        List<Chat> chats
) {

    public record Chat(
            int number,
            UUID chatId,
            String name
    ) {
    }
}
