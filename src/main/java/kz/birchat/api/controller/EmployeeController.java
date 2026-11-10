package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.service.EmployeeService;
import kz.birchat.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies/{companyId}/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public List<EmployeeResponse> getEmployees(
            @PathVariable UUID companyId,
            @CurrentUser UUID userId
    ) {
        return employeeService.getEmployees(companyId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse addEmployee(
            @PathVariable UUID companyId,
            @CurrentUser UUID actorUserId,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        return employeeService.addEmployee(companyId, actorUserId, request);
    }
}