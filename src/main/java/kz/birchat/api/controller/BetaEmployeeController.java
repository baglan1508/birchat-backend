package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.service.BetaIdentityService;
import kz.birchat.api.service.EmployeeService;
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
@RequestMapping("/api/companies/{companyId}/employees")
public class BetaEmployeeController {
    private final EmployeeService employees;
    private final BetaIdentityService identity;

    @GetMapping
    public List<EmployeeResponse> list(@PathVariable UUID companyId,
                                       @AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) UUID userId) {
        return employees.getEmployees(companyId, identity.currentUserId(jwt, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse add(@PathVariable UUID companyId,
                                @AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) UUID actorUserId,
                                @Valid @RequestBody CreateEmployeeRequest request) {
        UUID actor = identity.currentUserId(jwt, actorUserId);
        identity.requireDirector(companyId, actor);
        return employees.addEmployee(companyId, actor, request);
    }
}
