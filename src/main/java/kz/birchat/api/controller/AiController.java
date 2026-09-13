package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.dto.AiDirectorSummaryResponse;
import kz.birchat.api.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/ai")
public class AiController {

    private final AiService aiService;

    @PostMapping("/ask")
    public AiAskResponse ask(
            @PathVariable UUID companyId,
            @RequestParam UUID userId,
            @Valid @RequestBody AiAskRequest request
    ) {
        return aiService.ask(
                companyId,
                userId,
                request
        );
    }

    @GetMapping("/director/summary/today")
    public AiDirectorSummaryResponse getTodayDirectorSummary(
            @PathVariable UUID companyId,
            @RequestParam UUID userId
    ) {
        return aiService.getTodayDirectorSummary(
                companyId,
                userId
        );
    }
}