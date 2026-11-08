package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.*;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("beta")
@RequiredArgsConstructor
@RequestMapping("/api/companies")
public class BetaCompanyController {
    private final CompanyService companies;
    private final BetaIdentityService identity;

    @GetMapping("/my")
    public List<CompanyResponse> my(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) UUID userId) {
        return companies.getMyCompanies(identity.currentUserId(jwt, userId));
    }

    @GetMapping("/{companyId}/home")
    public CompanyHomeResponse home(@PathVariable UUID companyId,
                                    @AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) UUID userId) {
        return companies.getCompanyHome(companyId, identity.currentUserId(jwt, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody BetaCreateCompanyRequest request) {
        UUID userId = identity.currentUserId(jwt, request.ownerUserId());
        return companies.createCompany(new CreateCompanyRequest(
                userId, request.name(), request.field(), request.logoUrl(), request.color()));
    }
}
