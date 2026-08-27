package kz.birchat.api.controller;

import jakarta.validation.Valid;
import kz.birchat.api.dto.CreateEmployeeRequest;
import kz.birchat.api.dto.EmployeeResponse;
import kz.birchat.api.service.EmployeeService;
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
            @PathVariable UUID companyId
    ) {
        return employeeService.getEmployees(companyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse addEmployee(
            @PathVariable UUID companyId,
            @RequestParam UUID actorUserId,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        return employeeService.addEmployee(companyId, actorUserId, request);
    }
}