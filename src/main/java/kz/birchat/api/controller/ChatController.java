package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.*;
import kz.birchat.api.service.ChatService;
import kz.birchat.api.auth.CurrentUser;
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