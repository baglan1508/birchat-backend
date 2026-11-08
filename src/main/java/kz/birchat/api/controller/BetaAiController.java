package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.*;
import kz.birchat.api.service.AiService;
import kz.birchat.api.service.BetaIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/ai")
public class BetaAiController {
    private final AiService ai;
    private final BetaIdentityService identity;

    @PostMapping("/ask")
    public AiAskResponse ask(@PathVariable UUID companyId,
                             @AuthenticationPrincipal Jwt jwt,
                             @RequestParam(required = false) UUID userId,
                             @Valid @RequestBody AiAskRequest request) {
        return ai.ask(companyId, identity.currentUserId(jwt, userId), request);
    }

    @GetMapping("/history")
    public AiHistoryResponse history(@PathVariable UUID companyId,
                                     @AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) UUID userId,
                                     @RequestParam(defaultValue = "50") Integer limit) {
        return ai.getHistory(companyId, identity.currentUserId(jwt, userId), limit);
    }

    @GetMapping("/director/summary/today")
    public AiDirectorSummaryResponse directorSummary(@PathVariable UUID companyId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) UUID userId) {
        UUID actor = identity.currentUserId(jwt, userId);
        identity.requireDirector(companyId, actor);
        return ai.getTodayDirectorSummary(companyId, actor);
    }
}
