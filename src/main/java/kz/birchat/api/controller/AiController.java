package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.dto.AiDirectorSummaryResponse;
import kz.birchat.api.dto.AiHistoryResponse;
import kz.birchat.api.service.AiQuotaService;
import kz.birchat.api.service.AiService;
import kz.birchat.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/ai")
public class AiController {

    private final AiService aiService;
    private final AiQuotaService aiQuotaService;

    @PostMapping("/ask")
    public AiAskResponse ask(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody AiAskRequest request
    ) {
        aiQuotaService.consumeForAsk(companyId, userId, request);

        return aiService.ask(
                companyId,
                userId,
                request
        );
    }

    @GetMapping("/director/summary/today")
    public AiDirectorSummaryResponse getTodayDirectorSummary(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId
    ) {
        return aiService.getTodayDirectorSummary(
                companyId,
                userId
        );
    }

    /**
     * Сегодняшний диалог с AI. order=oldest (по умолчанию) — как в чате, от старых к новым;
     * order=newest — новые вопросы сверху.
     */
    @GetMapping("/history")
    public AiHistoryResponse getHistory(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = AiService.HISTORY_ORDER_OLDEST) String order
    ) {
        return aiService.getHistory(
                companyId,
                userId,
                limit,
                order
        );
    }

    /** «Новый диалог»: удалить свои вопросы и ответы AI в этой компании. */
    @PostMapping("/history/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId
    ) {
        aiService.clearHistory(companyId, userId);
    }
}