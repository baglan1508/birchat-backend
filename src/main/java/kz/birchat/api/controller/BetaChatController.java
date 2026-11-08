package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.*;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/chats")
public class BetaChatController {
    private final ChatService chats;
    private final BetaIdentityService identity;

    @GetMapping("/general/messages")
    public List<ChatMessageResponse> messages(@PathVariable UUID companyId,
                                              @AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) UUID userId,
                                              @RequestParam(required = false) UUID after,
                                              @RequestParam(required = false) UUID before,
                                              @RequestParam(required = false) Integer limit) {
        return chats.getGeneralChatMessages(companyId, identity.currentUserId(jwt, userId), after, before, limit);
    }

    @PostMapping("/general/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(@PathVariable UUID companyId,
                                    @AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody BetaCreateChatMessageRequest request) {
        UUID userId = identity.currentUserId(jwt, request.userId());
        return chats.createGeneralChatMessage(companyId, new CreateChatMessageRequest(userId, request.text()));
    }

    @PostMapping("/general/read")
    public ChatReadStateResponse read(@PathVariable UUID companyId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID userId,
                                      @Valid @RequestBody MarkChatReadRequest request) {
        return chats.markGeneralChatAsRead(companyId, identity.currentUserId(jwt, userId), request);
    }

    @PostMapping("/general/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendFile(@PathVariable UUID companyId,
                                        @AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody BetaCreateFileMessageRequest request) {
        UUID userId = identity.currentUserId(jwt, request.userId());
        return chats.createGeneralChatFileMessage(companyId,
                new CreateFileMessageRequest(userId, request.fileId(), request.text()));
    }
}
