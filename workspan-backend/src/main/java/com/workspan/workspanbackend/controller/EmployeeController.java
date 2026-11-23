package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.EmployeeRequest;
import com.workspan.workspanbackend.dto.EmployeeInviteRequest;
import com.workspan.workspanbackend.dto.EmployeeEmailRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRequest;
import com.workspan.workspanbackend.dto.EmployeeImportResponse;
import com.workspan.workspanbackend.dto.EmployeeResponse;
import com.workspan.workspanbackend.dto.CredentialBatchResponse;
import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.dto.RoleResponse;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.service.EmployeeService;
import com.workspan.workspanbackend.service.EmployeeCredentialService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import com.workspan.workspanbackend.service.AccessControlService;
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
    private final AccessControlService accessControl;

    public EmployeeController(EmployeeService employees, EmployeeCredentialService credentials, WorkspaceAccess access, AccessControlService accessControl) {
        this.employees = employees;
        this.credentials = credentials;
        this.access = access;
        this.accessControl = accessControl;
    }

    @GetMapping
    @Operation(summary = "Lister les employés de l’organisation")
    public List<EmployeeResponse> findAll(HttpSession session) {
        java.util.UUID userId = access.userId(session); java.util.UUID organizationId = access.organizationId(session);
        return employees.findAll(organizationId).stream().filter(employee -> access.hasSpacePermission(userId, organizationId, employee.spaceId(), "employee.view")
                || access.hasSpacePermission(userId, organizationId, employee.spaceId(), "employee.manage_accounts")).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un employé dans un espace de l’organisation")
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request, HttpSession session) {
        return employees.create(access.requireSpacePermission(session, request.spaceId(), "employee.create"), request);
    }

    @PostMapping("/import")
    @Operation(summary = "Importer jusqu'a 500 employes et ignorer les doublons")
    public EmployeeImportResponse importEmployees(@Valid @RequestBody EmployeeImportRequest request, HttpSession session) {
        java.util.UUID organizationId = access.organizationId(session);
        request.employees().forEach(row -> access.requireSpacePermission(session, row.spaceId(), "employee.create"));
        return employees.importEmployees(organizationId, request);
    }

    @PatchMapping("/{employeeId}/email")
    @Operation(summary = "Ajouter ou corriger l'e-mail d'un employe sans compte")
    public EmployeeResponse updateEmail(@PathVariable java.util.UUID employeeId,
                                        @Valid @RequestBody EmployeeEmailRequest request, HttpSession session) {
        java.util.UUID organizationId = access.organizationId(session);
        EmployeeResponse employee = employees.findAll(organizationId).stream().filter(item -> item.id().equals(employeeId)).findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND));
        access.requireSpacePermission(session, employee.spaceId(), "employee.manage_accounts");
        return employees.updateEmail(organizationId, employeeId, request);
    }

    @PostMapping("/credentials/send-all")
    @Operation(summary = "Send temporary credentials to employees not yet invited")
    public CredentialBatchResponse sendCredentialsToAll(HttpSession session) {
        return credentials.sendAll(access.requireOrganizationPermission(session, "employee.manage_accounts"));
    }

    @PostMapping("/{employeeId}/credentials/send")
    @Operation(summary = "Generate and send temporary credentials to one employee")
    public CredentialDeliveryResult sendCredentials(@PathVariable java.util.UUID employeeId, HttpSession session) {
        java.util.UUID organizationId = access.organizationId(session);
        EmployeeResponse employee = employees.findAll(organizationId).stream().filter(item -> item.id().equals(employeeId)).findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND));
        access.requireSpacePermission(session, employee.spaceId(), "employee.manage_accounts");
        return credentials.sendOne(organizationId, employeeId);
    }

    @GetMapping("/{employeeId}/invite-roles")
    @Operation(summary = "List roles the current administrator can assign while inviting this employee")
    public List<RoleResponse> inviteRoles(@PathVariable java.util.UUID employeeId, HttpSession session) {
        java.util.UUID organizationId = access.organizationId(session);
        EmployeeResponse employee = findEmployee(organizationId, employeeId);
        access.requireSpacePermission(session, employee.spaceId(), "employee.manage_accounts");
        java.util.UUID userId = access.userId(session);
        java.util.List<RoleResponse> available = new java.util.ArrayList<>();
        if (access.hasOrganizationPermission(userId, organizationId, "access.manage"))
            available.addAll(accessControl.roles(organizationId, RoleScope.ORGANIZATION, organizationId));
        if (access.hasSpacePermission(userId, organizationId, employee.spaceId(), "access.manage"))
            available.addAll(accessControl.roles(organizationId, RoleScope.SPACE, employee.spaceId()));
        return available.stream().filter(role -> !isReserved(role.name()) && !role.permissionKeys().isEmpty()).toList();
    }

    @PostMapping("/{employeeId}/invite")
    @Operation(summary = "Assign an access role and invite an employee in one operation")
    public CredentialDeliveryResult invite(@PathVariable java.util.UUID employeeId,
            @Valid @RequestBody EmployeeInviteRequest request, HttpSession session) {
        java.util.UUID organizationId = access.organizationId(session);
        EmployeeResponse employee = findEmployee(organizationId, employeeId);
        access.requireSpacePermission(session, employee.spaceId(), "employee.manage_accounts");
        if (employee.hasAccount()) throw new ConflictException(ErrorCode.EMPLOYEE_ALREADY_HAS_ACCOUNT);
        if (request.roleScope() == RoleScope.ORGANIZATION)
            access.requireScopeManager(session, RoleScope.ORGANIZATION, organizationId);
        else if (request.roleScope() == RoleScope.SPACE)
            access.requireScopeManager(session, RoleScope.SPACE, employee.spaceId());
        else throw new BadRequestException(ErrorCode.INVITATION_ROLE_REQUIRED);
        return credentials.invite(organizationId, employeeId, request.roleId(), request.roleScope());
    }

    private EmployeeResponse findEmployee(java.util.UUID organizationId, java.util.UUID employeeId) {
        return employees.findAll(organizationId).stream().filter(item -> item.id().equals(employeeId)).findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    private boolean isReserved(String name) {
        String normalized = name.toUpperCase(java.util.Locale.ROOT);
        return normalized.equals("ADMIN") || normalized.equals("EMPLOYEE")
                || normalized.startsWith("SPACE_ADMIN - ") || normalized.startsWith("PROJECT_ADMIN - ");
    }
}
