package kz.birchat.api.controller;

import kz.birchat.api.dto.SearchResultResponse;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/search")
public class BetaSearchController {
    private final SearchService searchService;
    private final BetaIdentityService identity;

    @GetMapping
    public List<SearchResultResponse> search(@PathVariable UUID companyId,
                                             @AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) UUID userId,
                                             @RequestParam String query,
                                             @RequestParam(defaultValue = "20") Integer limit) {
        return searchService.searchBeta(companyId, identity.currentUserId(jwt, userId), query, limit);
    }
}
