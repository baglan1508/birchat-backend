package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CompanyHomeResponse;
import kz.birchat.api.dto.CompanyResponse;
import kz.birchat.api.dto.CreateCompanyRequest;
import kz.birchat.api.service.CompanyService;
import kz.birchat.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/my")
    public List<CompanyResponse> getMyCompanies(
            @CurrentUser UUID userId
    ) {
        return companyService.getMyCompanies(userId);
    }

    @GetMapping("/{companyId}/home")
    public CompanyHomeResponse getCompanyHome(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId
    ) {
        return companyService.getCompanyHome(companyId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateCompanyRequest request
    ) {
        return companyService.createCompany(userId, request);
    }
}