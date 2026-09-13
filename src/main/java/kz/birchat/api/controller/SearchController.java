package kz.birchat.api.controller;

import kz.birchat.api.dto.SearchResultResponse;
import kz.birchat.api.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public List<SearchResultResponse> search(
            @PathVariable UUID companyId,
            @RequestParam UUID userId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return searchService.search(
                companyId,
                userId,
                query,
                limit
        );
    }
}