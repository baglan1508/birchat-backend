package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
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

    @GetMapping("/general/messages")
    public List<ChatMessageResponse> getGeneralChatMessages(
            @PathVariable UUID companyId
    ) {
        return chatService.getGeneralChatMessages(companyId);
    }

    @PostMapping("/general/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse createGeneralChatMessage(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        return chatService.createGeneralChatMessage(companyId, request);
    }
}