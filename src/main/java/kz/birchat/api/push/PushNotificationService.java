package kz.birchat.api.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import kz.birchat.api.dto.ChatSummaryResponse;
import kz.birchat.api.entity.ChatAttachmentEntity;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageMentionRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.service.ChatAccessService;
import kz.birchat.api.service.ChatManagementService;
import kz.birchat.api.service.ChatMessageActionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Push-уведомления о новых сообщениях.
 *
 * Отправка идёт в фоне, после того как сообщение сохранено: отправитель сообщения не ждёт Apple,
 * и сбой пушей не мешает отправке сообщения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSender pushSender;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ChatPresenceTracker presenceTracker;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final ChatMessageActionsService chatMessageActionsService;
    private final ChatManagementService chatManagementService;
    private final ChatMessageMentionRepository chatMessageMentionRepository;
    private final FcmClient fcmClient;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    /** Два потока и очередь на 500 сообщений; если очередь переполнена, пуш пропускается. */
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "push-sender");
                thread.setDaemon(true);
                return thread;
            },
            (task, pool) -> log.warn("Push queue is full, a notification was skipped")
    );

    /** platform — IOS (payloadJson для APNs) или ANDROID (payloadJson — объект «message» FCM без token). */
    record Outgoing(String deviceToken, String environment, String payloadJson, String platform) {

        Outgoing(String deviceToken, String environment, String payloadJson) {
            this(deviceToken, environment, payloadJson, DeviceService.PLATFORM_IOS);
        }
    }

    /** Последний бейдж, отправленный пользователю, — чтобы не слать тихий пуш с той же цифрой. */
    private final Map<UUID, Integer> lastBadge = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Пользователь что-то прочитал: тихий пуш с новым бейджем на его устройства, чтобы цифра
     * на иконке не висела, когда всё прочитано на другом телефоне. Ничего не бросает.
     */
    public void badgeChanged(UUID userId) {
        if (userId == null || !pushSender.isConfigured()) {
            return;
        }

        executor.execute(() -> {
            try {
                deliverBadge(userId);
            } catch (Exception ex) {
                log.error("Badge push failed. userId={}", userId, ex);
            }
        });
    }

    void deliverBadge(UUID userId) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);

        record Badge(List<DeviceTokenEntity> devices, int badge) {
        }

        Badge badge = readTx.execute(status -> {
            // Бейдж на Android рисует лаунчер сам — тихий пуш только на iPhone.
            List<DeviceTokenEntity> devices = deviceTokenRepository.findByUserIdIn(Set.of(userId)).stream()
                    .filter(device -> !DeviceService.PLATFORM_ANDROID.equals(device.getPlatform()))
                    .toList();
            return devices.isEmpty() ? null : new Badge(devices, unreadTotal(userId));
        });

        if (badge == null) {
            return;
        }

        Integer previous = rememberBadge(userId, badge.badge());

        if (previous != null && previous == badge.badge()) {
            return;
        }

        String payload = toJson(Map.<String, Object>of("aps", Map.of("badge", badge.badge())));
        List<String> invalidTokens = new ArrayList<>();

        for (DeviceTokenEntity device : badge.devices()) {
            if (pushSender.sendQuiet(device.getToken(), device.getEnvironment(), payload)
                    == PushSender.Result.INVALID_TOKEN) {
                invalidTokens.add(device.getToken());
            }
        }

        if (!invalidTokens.isEmpty()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    invalidTokens.forEach(deviceTokenRepository::deleteByTokenValue));
        }
    }

    private Integer rememberBadge(UUID userId, int badge) {
        if (lastBadge.size() > 50_000) {
            lastBadge.clear();
        }

        return lastBadge.put(userId, badge);
    }

    private PushSender.Result send(Outgoing notification) {
        if (DeviceService.PLATFORM_ANDROID.equals(notification.platform())) {
            try {
                return fcmClient.send(notification.deviceToken(), objectMapper.readTree(notification.payloadJson()));
            } catch (Exception ex) {
                log.warn("FCM payload error: {}", ex.toString());
                return PushSender.Result.FAILED;
            }
        }

        return pushSender.send(notification.deviceToken(), notification.environment(), notification.payloadJson());
    }

    private boolean canSend(DeviceTokenEntity device) {
        return DeviceService.PLATFORM_ANDROID.equals(device.getPlatform())
                ? fcmClient.isConfigured()
                : pushSender.isConfigured();
    }

    /** Вызывается после сохранения сообщения. Ничего не бросает. */
    public void messageCreated(UUID messageId) {
        if (messageId == null || (!pushSender.isConfigured() && !fcmClient.isConfigured())) {
            return;
        }

        executor.execute(() -> {
            try {
                deliver(messageId);
            } catch (Exception ex) {
                log.error("Push delivery failed. messageId={}", messageId, ex);
            }
        });
    }

    void deliver(UUID messageId) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);

        List<Outgoing> notifications = readTx.execute(status -> prepare(messageId));

        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        List<String> invalidTokens = new ArrayList<>();

        // Отправка — вне транзакции: соединение с базой не занято, пока отвечает Apple.
        for (Outgoing notification : notifications) {
            PushSender.Result result = send(notification);

            if (result == PushSender.Result.INVALID_TOKEN) {
                invalidTokens.add(notification.deviceToken());
            }
        }

        if (!invalidTokens.isEmpty()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    invalidTokens.forEach(deviceTokenRepository::deleteByTokenValue));
            log.info("Removed {} device tokens rejected by APNs", invalidTokens.size());
        }
    }

    /** Кому и что отправить: участники чата, кроме автора и тех, у кого чат сейчас открыт. */
    List<Outgoing> prepare(UUID messageId) {
        ChatMessageEntity message = chatMessageRepository.findAllWithUserAndChatByIdIn(List.of(messageId))
                .stream()
                .findFirst()
                .orElse(null);

        if (message == null || Boolean.TRUE.equals(message.getIsDeleted())) {
            return List.of();
        }

        ChatEntity chat = message.getChat();
        UUID companyId = chat.getCompany().getId();
        UUID authorId = message.getUser().getId();
        boolean general = ChatAccessService.isGeneral(chat);

        boolean direct = ChatAccessService.isDirect(chat);

        Set<UUID> recipients = chatMessageActionsService.recipients(companyId, chat);
        recipients.remove(authorId);

        // Упомянутому push приходит, даже если чат у него открыт.
        Set<UUID> mentioned = new HashSet<>();
        chatMessageMentionRepository.findByMessageIds(List.of(messageId))
                .forEach(mention -> mentioned.add(mention.getUser().getId()));
        mentioned.retainAll(recipients);

        recipients.removeIf(userId -> !mentioned.contains(userId)
                && presenceTracker.isInChat(userId, chat.getId(), general ? companyId : null));

        if (recipients.isEmpty()) {
            return List.of();
        }

        List<DeviceTokenEntity> devices = deviceTokenRepository.findByUserIdIn(recipients);

        if (devices.isEmpty()) {
            return List.of();
        }

        String fileName = chatAttachmentRepository.findByMessageIds(List.of(messageId))
                .stream()
                .map(ChatAttachmentEntity::getFile)
                .filter(file -> file != null)
                .map(file -> file.getOriginalFileName())
                .findFirst()
                .orElse(null);

        String companyName = chat.getCompany().getName();
        String authorName = message.getUser().getDisplayName();
        Map<UUID, Integer> badges = new HashMap<>();
        List<Outgoing> result = new ArrayList<>();

        for (DeviceTokenEntity device : devices) {
            if (!canSend(device)) {
                continue;
            }

            int badge = badges.computeIfAbsent(device.getUserId(), userId -> {
                int total = unreadTotal(userId);
                rememberBadge(userId, total);
                return total;
            });
            String body;

            if (mentioned.contains(device.getUserId())) {
                body = PushTextBuilder.mentionBody(authorName, message.getType(), message.getContent(), fileName, device.getLocale());
            } else if (direct) {
                body = PushTextBuilder.text(message.getType(), message.getContent(), fileName, device.getLocale());
            } else {
                body = PushTextBuilder.body(authorName, message.getType(), message.getContent(), fileName, device.getLocale());
            }

            // Личный чат: заголовок — имя автора, без названия чата.
            String title = direct ? authorName : companyName;
            String subtitle = general || direct ? null : chat.getName();

            if (DeviceService.PLATFORM_ANDROID.equals(device.getPlatform())) {
                // В Android нет subtitle: название группы идёт в заголовок через « · ».
                String androidTitle = subtitle == null ? title : title + " · " + subtitle;
                result.add(new Outgoing(
                        device.getToken(),
                        device.getEnvironment(),
                        toJson(androidMessage(androidTitle, body, companyId, chat.getId(), messageId)),
                        DeviceService.PLATFORM_ANDROID
                ));
                continue;
            }

            result.add(new Outgoing(
                    device.getToken(),
                    device.getEnvironment(),
                    toJson(payload(title, subtitle, body, badge, companyId, chat.getId(), messageId))
            ));
        }

        return result;
    }

    /** Цифра на иконке: непрочитанные по всем чатам всех компаний пользователя. */
    int unreadTotal(UUID userId) {
        long total = 0;

        for (CompanyMemberEntity membership : companyMemberRepository.findByUserIdAndStatus(userId, "ACTIVE")) {
            for (ChatSummaryResponse chat : chatManagementService.getChats(membership.getCompany().getId(), userId)) {
                total += chat.unreadCount() == null ? 0 : chat.unreadCount();
            }
        }

        return (int) Math.min(total, 9999);
    }

    /**
     * Сообщение FCM (без token). data — строки, title/body продублированы в data: если приложение открыто,
     * уведомление показывает само приложение. tag = chatId: новое сообщение чата заменяет прежнее.
     */
    static Map<String, Object> androidMessage(String title, String body, UUID companyId, UUID chatId, UUID messageId) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title);
        notification.put("body", body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", companyId.toString());
        data.put("chatId", chatId.toString());
        data.put("messageId", messageId.toString());
        data.put("title", title);
        data.put("body", body);

        Map<String, Object> androidNotification = new LinkedHashMap<>();
        androidNotification.put("channel_id", "messages");
        androidNotification.put("tag", chatId.toString());

        Map<String, Object> android = new LinkedHashMap<>();
        android.put("priority", "HIGH");
        android.put("notification", androidNotification);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("notification", notification);
        message.put("data", data);
        message.put("android", android);
        return message;
    }

    /**
     * title — компания, subtitle — группа (у общего чата нет), thread-id — чат (iOS группирует по чатам);
     * companyId, chatId, messageId — чтобы приложение открыло нужный чат по нажатию.
     */
    static Map<String, Object> payload(
            String companyName,
            String groupName,
            String body,
            int badge,
            UUID companyId,
            UUID chatId,
            UUID messageId
    ) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", companyName);

        if (groupName != null && !groupName.isBlank()) {
            alert.put("subtitle", groupName);
        }

        alert.put("body", body);

        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("badge", badge);
        aps.put("sound", "default");
        aps.put("thread-id", chatId.toString());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        root.put("companyId", companyId.toString());
        root.put("chatId", chatId.toString());
        root.put("messageId", messageId.toString());

        return root;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot build push payload", ex);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
