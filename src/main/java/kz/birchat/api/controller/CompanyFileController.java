package kz.birchat.api.controller;

import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.service.CompanyFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/files")
public class CompanyFileController {

    private final CompanyFileService companyFileService;

    @GetMapping
    public List<CompanyFileResponse> getCompanyFiles(
            @PathVariable UUID companyId,
            @RequestParam UUID userId,
            @RequestParam(required = false) Integer limit
    ) {
        return companyFileService.getCompanyFiles(companyId, userId, limit);
    }

    @GetMapping("/{fileId}")
    public CompanyFileResponse getCompanyFile(
            @PathVariable UUID companyId,
            @PathVariable UUID fileId,
            @RequestParam UUID userId
    ) {
        return companyFileService.getCompanyFile(companyId, fileId, userId);
    }
}