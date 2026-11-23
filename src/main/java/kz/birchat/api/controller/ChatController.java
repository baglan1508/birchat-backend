package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.auth.CurrentUser;
import kz.birchat.api.dto.*;
import kz.birchat.api.push.ChatPresenceTracker;
import kz.birchat.api.push.PushNotificationService;
import kz.birchat.api.realtime.RealtimeEventService;
import kz.birchat.api.service.ChatManagementService;
import kz.birchat.api.service.ChatMessageActionsService;
import kz.birchat.api.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/chats")
public class ChatController {

    private final ChatService chatService;
    private final ChatManagementService chatManagementService;
    private final ChatMessageActionsService chatMessageActionsService;
    private final PushNotificationService pushNotificationService;
    private final ChatPresenceTracker chatPresenceTracker;
    private final RealtimeEventService realtimeEventService;

    // ---- список чатов и групповые чаты ----

    @GetMapping
    public List<ChatSummaryResponse> getChats(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId
    ) {
        return chatManagementService.getChats(companyId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDetailsResponse createGroupChat(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateGroupChatRequest request
    ) {
        return chatManagementService.createGroupChat(companyId, userId, request);
    }

    /** Личный чат с коллегой: существующий или новый. */
    @PostMapping("/direct")
    public ChatDetailsResponse openDirectChat(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateDirectChatRequest request
    ) {
        return chatManagementService.openDirectChat(companyId, userId, request.userId());
    }

    @GetMapping("/{chatId}")
    public ChatDetailsResponse getChat(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId
    ) {
        return chatManagementService.getChat(companyId, chatId, userId);
    }

    /** Переименовать группу (только директор). */
    @PatchMapping("/{chatId}")
    public ChatDetailsResponse renameChat(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody RenameChatRequest request
    ) {
        ChatDetailsResponse chat = chatManagementService.renameChat(companyId, chatId, userId, request.name());
        realtimeEventService.chatRenamed(chatId, chat.name());
        return chat;
    }

    @PostMapping("/{chatId}/members")
    public ChatDetailsResponse addMembers(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody AddChatMembersRequest request
    ) {
        return chatManagementService.addMembers(companyId, chatId, userId, request);
    }

    @DeleteMapping("/{chatId}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @PathVariable UUID memberUserId,
            @CurrentUser UUID userId
    ) {
        chatManagementService.removeMember(companyId, chatId, userId, memberUserId);
    }

    // ---- сообщения любого чата ----

    @GetMapping("/{chatId}/messages")
    public List<ChatMessageResponse> getMessages(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @RequestParam(required = false) UUID after,
            @RequestParam(required = false) UUID before,
            @RequestParam(required = false) Integer limit
    ) {
        List<ChatMessageResponse> messages = chatService.getMessages(companyId, chatId, userId, after, before, limit);
        chatPresenceTracker.touchChat(userId, chatId);
        return messages;
    }

    @PostMapping("/{chatId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return notified(chatService.createMessage(companyId, chatId, userId, request));
    }

    @PostMapping("/{chatId}/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createFileMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateFileMessageRequest request
    ) {
        return notified(chatService.createFileMessage(companyId, chatId, userId, request));
    }

    /** Изменить сообщение: только автор, не позже 48 часов. */
    @PatchMapping("/{chatId}/messages/{messageId}")
    public ChatMessageResponse editMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @CurrentUser UUID userId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        ChatMessageResponse message = chatService.editMessage(companyId, chatId, messageId, userId, request);
        realtimeEventService.messageUpdated(message);
        return message;
    }

    /** Какие сообщения чата изменены после since (для обновления экрана при polling). */
    @GetMapping("/{chatId}/messages/updated")
    public UpdatedMessagesResponse getUpdatedMessages(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME
            ) java.time.OffsetDateTime since
    ) {
        return chatService.getUpdatedSince(companyId, chatId, userId, since);
    }

    /** Удалить сообщение у всех: автор или директор. */
    @DeleteMapping("/{chatId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @CurrentUser UUID userId
    ) {
        chatMessageActionsService.deleteForEveryone(companyId, chatId, messageId, userId);
        realtimeEventService.messageDeleted(chatId, messageId);
    }

    /** Какие сообщения чата удалены у всех после since (для обновления экрана при polling). */
    @GetMapping("/{chatId}/messages/deleted")
    public DeletedMessagesResponse getDeletedMessages(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME
            ) java.time.OffsetDateTime since
    ) {
        return chatMessageActionsService.getDeletedSince(companyId, chatId, userId, since);
    }

    /** Кто прочитал сообщение. */
    @GetMapping("/{chatId}/messages/{messageId}/readers")
    public MessageReadersResponse getReaders(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @CurrentUser UUID userId
    ) {
        return chatMessageActionsService.getReaders(companyId, chatId, messageId, userId);
    }

    @PostMapping("/{chatId}/read")
    public ChatReadStateResponse markAsRead(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody MarkChatReadRequest request
    ) {
        return readNotified(chatService.markAsRead(companyId, chatId, userId, request));
    }

    // ---- общий чат: прежние адреса для совместимости с приложением ----

    @GetMapping("/general/messages")
    public List<ChatMessageResponse> getGeneralChatMessages(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @RequestParam(required = false) UUID after,
            @RequestParam(required = false) UUID before,
            @RequestParam(required = false) Integer limit
    ) {
        List<ChatMessageResponse> messages = chatService.getGeneralChatMessages(companyId, userId, after, before, limit);
        chatPresenceTracker.touchGeneralChat(userId, companyId);
        return messages;
    }

    @PostMapping("/general/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createGeneralChatMessage(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return notified(chatService.createGeneralChatMessage(companyId, userId, request));
    }

    @PostMapping("/general/read")
    public ChatReadStateResponse markGeneralChatAsRead(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody MarkChatReadRequest request
    ) {
        return readNotified(chatService.markGeneralChatAsRead(companyId, userId, request));
    }

    @PostMapping("/general/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createGeneralChatFileMessage(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateFileMessageRequest request
    ) {
        return notified(chatService.createGeneralChatFileMessage(companyId, userId, request));
    }

    private ChatReadStateResponse readNotified(ChatReadStateResponse state) {
        realtimeEventService.chatRead(state);
        return state;
    }

    /** Сообщение уже сохранено — в фоне отправить push и событие по WebSocket участникам чата. */
    private ChatMessageResponse notified(ChatMessageResponse message) {
        pushNotificationService.messageCreated(message.id());
        realtimeEventService.messageCreated(message);
        return message;
    }
}
