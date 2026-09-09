package kz.birchat.api.controller;

import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.service.CompanyFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import kz.birchat.api.dto.CompanyFileDownloadUrlResponse;

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

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyFileResponse uploadFile(
            @PathVariable UUID companyId,
            @RequestParam UUID userId,
            @RequestParam("file") MultipartFile file
    ) {
        return companyFileService.uploadFile(companyId, userId, file);
    }

    @GetMapping("/{fileId}/download-url")
    public CompanyFileDownloadUrlResponse getDownloadUrl(
            @PathVariable UUID companyId,
            @PathVariable UUID fileId,
            @RequestParam UUID userId,
            @RequestParam(required = false) Integer expiresInSeconds
    ) {
        return companyFileService.getDownloadUrl(
                companyId,
                fileId,
                userId,
                expiresInSeconds
        );
    }
}