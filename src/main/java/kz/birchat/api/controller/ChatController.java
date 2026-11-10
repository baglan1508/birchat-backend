package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.auth.CurrentUser;
import kz.birchat.api.dto.*;
import kz.birchat.api.service.ChatManagementService;
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

    @GetMapping("/{chatId}")
    public ChatDetailsResponse getChat(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId
    ) {
        return chatManagementService.getChat(companyId, chatId, userId);
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
        return chatService.getMessages(companyId, chatId, userId, after, before, limit);
    }

    @PostMapping("/{chatId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return chatService.createMessage(companyId, chatId, userId, request);
    }

    @PostMapping("/{chatId}/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createFileMessage(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateFileMessageRequest request
    ) {
        return chatService.createFileMessage(companyId, chatId, userId, request);
    }

    @PostMapping("/{chatId}/read")
    public ChatReadStateResponse markAsRead(
            @PathVariable UUID companyId,
            @PathVariable UUID chatId,
            @CurrentUser UUID userId,
            @Valid @RequestBody MarkChatReadRequest request
    ) {
        return chatService.markAsRead(companyId, chatId, userId, request);
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
        return chatService.getGeneralChatMessages(companyId, userId, after, before, limit);
    }

    @PostMapping("/general/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createGeneralChatMessage(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return chatService.createGeneralChatMessage(companyId, userId, request);
    }

    @PostMapping("/general/read")
    public ChatReadStateResponse markGeneralChatAsRead(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody MarkChatReadRequest request
    ) {
        return chatService.markGeneralChatAsRead(companyId, userId, request);
    }

    @PostMapping("/general/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createGeneralChatFileMessage(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateFileMessageRequest request
    ) {
        return chatService.createGeneralChatFileMessage(companyId, userId, request);
    }
}
