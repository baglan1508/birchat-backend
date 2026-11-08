package kz.birchat.api.controller;

import kz.birchat.api.dto.CompanyFileDownloadUrlResponse;
import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.CompanyFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/files")
public class BetaCompanyFileController {
    private final CompanyFileService files;
    private final BetaIdentityService identity;

    @GetMapping
    public List<CompanyFileResponse> list(@PathVariable UUID companyId,
                                          @AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) UUID userId,
                                          @RequestParam(required = false) Integer limit) {
        return files.getCompanyFiles(companyId, identity.currentUserId(jwt, userId), limit);
    }

    @GetMapping("/{fileId}")
    public CompanyFileResponse file(@PathVariable UUID companyId,
                                    @PathVariable UUID fileId,
                                    @AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) UUID userId) {
        return files.getCompanyFile(companyId, fileId, identity.currentUserId(jwt, userId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyFileResponse upload(@PathVariable UUID companyId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID userId,
                                      @RequestParam("file") MultipartFile file) {
        return files.uploadFile(companyId, identity.currentUserId(jwt, userId), file);
    }

    @GetMapping("/{fileId}/download-url")
    public CompanyFileDownloadUrlResponse download(@PathVariable UUID companyId,
                                                   @PathVariable UUID fileId,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) UUID userId,
                                                   @RequestParam(required = false) Integer expiresInSeconds) {
        return files.getDownloadUrl(companyId, fileId,
                identity.currentUserId(jwt, userId), expiresInSeconds);
    }
}
