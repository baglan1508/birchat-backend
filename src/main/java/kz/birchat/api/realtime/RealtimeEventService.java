package kz.birchat.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.ChatReadStateResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.service.ChatMessageActionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * События для приложения по WebSocket. Каждое событие — JSON с полями type, companyId, chatId:
 *
 *   message.created  — message: то же, что отдаёт GET …/messages (приходит и автору — на другие его устройства);
 *   message.deleted  — messageId;
 *   chat.read        — userId, lastReadMessageId, lastReadMessageCreatedAt (кто-то прочитал чат);
 *   chat.renamed     — name.
 *
 * Получатели — все, кто сейчас в чате. Отправка — в фоне одним потоком, чтобы события шли по порядку
 * и запрос, вызвавший событие, не ждал.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventService {

    private final RealtimeSessionRegistry registry;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageActionsService chatMessageActionsService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2000),
            runnable -> {
                Thread thread = new Thread(runnable, "realtime-events");
                thread.setDaemon(true);
                return thread;
            },
            (task, pool) -> log.warn("Realtime queue is full, an event was skipped")
    );

    public void messageCreated(ChatMessageResponse message) {
        if (message == null) {
            return;
        }

        publish(
                () -> chatMessageRepository.findAllWithUserAndChatByIdIn(java.util.List.of(message.id()))
                        .stream()
                        .findFirst()
                        .map(found -> found.getChat())
                        .orElse(null),
                "message.created",
                Map.of("message", message)
        );
    }

    public void messageDeleted(UUID chatId, UUID messageId) {
        publish(() -> chatRepository.findById(chatId).orElse(null), "message.deleted",
                Map.of("messageId", messageId.toString()));
    }

    public void chatRead(ChatReadStateResponse state) {
        if (state == null || state.chatId() == null) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", state.userId());
        fields.put("lastReadMessageId", state.lastReadMessageId());
        fields.put("lastReadMessageCreatedAt", state.lastReadMessageCreatedAt());

        publish(() -> chatRepository.findById(state.chatId()).orElse(null), "chat.read", fields);
    }

    public void chatRenamed(UUID chatId, String name) {
        publish(() -> chatRepository.findById(chatId).orElse(null), "chat.renamed", Map.of("name", name));
    }

    private void publish(Supplier<ChatEntity> chatLoader, String type, Map<String, Object> fields) {
        // Никто не подключён — не тратим запросы к базе.
        if (registry.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                deliver(chatLoader, type, fields);
            } catch (Exception ex) {
                log.error("Realtime event {} failed", type, ex);
            }
        });
    }

    void deliver(Supplier<ChatEntity> chatLoader, String type, Map<String, Object> fields) throws Exception {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);

        record Target(UUID companyId, UUID chatId, Set<UUID> recipients) {
        }

        Target target = readTx.execute(status -> {
            ChatEntity chat = chatLoader.get();

            if (chat == null) {
                return null;
            }

            UUID companyId = chat.getCompany().getId();

            return new Target(companyId, chat.getId(), chatMessageActionsService.recipients(companyId, chat));
        });

        if (target == null || target.recipients().isEmpty()) {
            return;
        }

        registry.send(target.recipients(), objectMapper.writeValueAsString(
                event(type, target.companyId(), target.chatId(), fields)
        ));
    }

    static Map<String, Object> event(String type, UUID companyId, UUID chatId, Map<String, Object> fields) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("companyId", companyId.toString());
        event.put("chatId", chatId.toString());
        event.putAll(fields);
        return event;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
