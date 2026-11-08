package kz.birchat.api.controller;

import org.springframework.context.annotation.Profile;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CompanyHomeResponse;
import kz.birchat.api.dto.CompanyResponse;
import kz.birchat.api.dto.CreateCompanyRequest;
import kz.birchat.api.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Profile("!beta")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/my")
    public List<CompanyResponse> getMyCompanies(
            @RequestParam UUID userId
    ) {
        return companyService.getMyCompanies(userId);
    }

    @GetMapping("/{companyId}/home")
    public CompanyHomeResponse getCompanyHome(
            @PathVariable UUID companyId,
            @RequestParam UUID userId
    ) {
        return companyService.getCompanyHome(companyId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @Valid @RequestBody CreateCompanyRequest request
    ) {
        return companyService.createCompany(request);
    }
}
