package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.dto.CreateUploadUrlRequest;
import kz.birchat.api.dto.DownloadUrlItem;
import kz.birchat.api.dto.DownloadUrlsRequest;
import kz.birchat.api.dto.UploadUrlResponse;
import kz.birchat.api.service.CompanyFileService;
import kz.birchat.api.auth.CurrentUser;
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
            @CurrentUser UUID userId,
            @RequestParam(required = false) Integer limit
    ) {
        return companyFileService.getCompanyFiles(companyId, userId, limit);
    }

    @GetMapping("/{fileId}")
    public CompanyFileResponse getCompanyFile(
            @PathVariable UUID companyId,
            @PathVariable UUID fileId,
            @CurrentUser UUID userId
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
            @CurrentUser UUID userId,
            @RequestParam(value = "chatId", required = false) UUID chatId,
            @RequestParam("file") MultipartFile file
    ) {
        return companyFileService.uploadFile(companyId, userId, chatId, file);
    }

    @GetMapping("/{fileId}/download-url")
    public CompanyFileDownloadUrlResponse getDownloadUrl(
            @PathVariable UUID companyId,
            @PathVariable UUID fileId,
            @CurrentUser UUID userId,
            @RequestParam(required = false) Integer expiresInSeconds
    ) {
        return companyFileService.getDownloadUrl(
                companyId,
                fileId,
                userId,
                expiresInSeconds
        );
    }

    /**
     * Прямая загрузка, шаг 1: ссылка, по которой телефон загрузит файл прямо в хранилище.
     */
    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createUploadUrl(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateUploadUrlRequest request
    ) {
        return companyFileService.createUploadUrl(companyId, userId, request);
    }

    /**
     * Прямая загрузка, шаг 3: файл загружен — проверить и открыть доступ.
     */
    @PostMapping("/{fileId}/complete")
    public CompanyFileResponse completeUpload(
            @PathVariable UUID companyId,
            @PathVariable UUID fileId,
            @CurrentUser UUID userId
    ) {
        return companyFileService.completeUpload(companyId, fileId, userId);
    }

    /**
     * Ссылки на скачивание для нескольких файлов сразу (показ фото внутри приложения).
     */
    @PostMapping("/download-urls")
    public List<DownloadUrlItem> getDownloadUrls(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId,
            @Valid @RequestBody DownloadUrlsRequest request
    ) {
        return companyFileService.getDownloadUrls(companyId, userId, request);
    }
}
