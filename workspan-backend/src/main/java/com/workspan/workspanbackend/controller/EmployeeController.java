package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.EmployeeRequest;
import com.workspan.workspanbackend.dto.EmployeeEmailRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRequest;
import com.workspan.workspanbackend.dto.EmployeeImportResponse;
import com.workspan.workspanbackend.dto.EmployeeResponse;
import com.workspan.workspanbackend.dto.CredentialBatchResponse;
import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.service.EmployeeService;
import com.workspan.workspanbackend.service.EmployeeCredentialService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Employés de l’organisation connectée")
@SecurityRequirement(name = "sessionCookie")
public class EmployeeController {
    private final EmployeeService employees;
    private final EmployeeCredentialService credentials;
    private final WorkspaceAccess access;

    public EmployeeController(EmployeeService employees, EmployeeCredentialService credentials, WorkspaceAccess access) {
        this.employees = employees;
        this.credentials = credentials;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Lister les employés de l’organisation")
    public List<EmployeeResponse> findAll(HttpSession session) {
        return employees.findAll(access.organizationId(session));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un employé dans un espace de l’organisation")
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request, HttpSession session) {
        return employees.create(access.organizationId(session), request);
    }

    @PostMapping("/import")
    @Operation(summary = "Importer jusqu'a 500 employes et ignorer les doublons")
    public EmployeeImportResponse importEmployees(@Valid @RequestBody EmployeeImportRequest request, HttpSession session) {
        return employees.importEmployees(access.organizationId(session), request);
    }

    @PatchMapping("/{employeeId}/email")
    @Operation(summary = "Ajouter ou corriger l'e-mail d'un employe sans compte")
    public EmployeeResponse updateEmail(@PathVariable java.util.UUID employeeId,
                                        @Valid @RequestBody EmployeeEmailRequest request, HttpSession session) {
        return employees.updateEmail(access.organizationAdminId(session), employeeId, request);
    }

    @PostMapping("/credentials/send-all")
    @Operation(summary = "Send temporary credentials to employees not yet invited")
    public CredentialBatchResponse sendCredentialsToAll(HttpSession session) {
        return credentials.sendAll(access.organizationAdminId(session));
    }

    @PostMapping("/{employeeId}/credentials/send")
    @Operation(summary = "Generate and send temporary credentials to one employee")
    public CredentialDeliveryResult sendCredentials(@PathVariable java.util.UUID employeeId, HttpSession session) {
        return credentials.sendOne(access.organizationAdminId(session), employeeId);
    }
}
