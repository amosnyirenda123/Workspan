package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.ApiException;
import com.workspan.workspanbackend.dto.EmployeeRequest;
import com.workspan.workspanbackend.dto.EmployeeEmailRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRow;
import com.workspan.workspanbackend.dto.EmployeeImportRowResult;
import com.workspan.workspanbackend.dto.EmployeeImportResponse;
import com.workspan.workspanbackend.dto.EmployeeResponse;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import com.workspan.workspanbackend.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeService {
    private final HumanResourceRepository employees;
    private final EmployeeWriter writer;
    private final UserAccountRepository users;

    public EmployeeService(HumanResourceRepository employees, EmployeeWriter writer, UserAccountRepository users) {
        this.employees = employees;
        this.writer = writer;
        this.users = users;
    }

    public List<EmployeeResponse> findAll(UUID organizationId) {
        return employees.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public EmployeeResponse create(UUID organizationId, EmployeeRequest request) {
        return writer.create(organizationId, new EmployeeImportRow(1, request.spaceId(), request.name(), request.email(), request.jobTitle()));
    }

    @Transactional
    public EmployeeResponse updateEmail(UUID organizationId, UUID employeeId, EmployeeEmailRequest request) {
        return writer.updateEmail(organizationId, employeeId, request);
    }

    @Transactional
    public EmployeeResponse setAccountEnabled(UUID organizationId, UUID employeeId, boolean enabled) {
        HumanResource employee = employees.findByIdAndSpaceOrganizationId(employeeId, organizationId)
                .orElseThrow(() -> new com.workspan.workspanbackend.exception.NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND));
        if (employee.getUser() == null) throw new ConflictException(ErrorCode.EMPLOYEE_ACCOUNT_NOT_FOUND);
        var account = employee.getUser();
        if (enabled) {
            if ("INVITED".equals(account.getStatus())) throw new ConflictException(ErrorCode.EMPLOYEE_ACCOUNT_NOT_FOUND);
            account.setStatus("ACTIVE"); employee.setStatus("AVAILABLE");
        } else {
            account.setStatus("INACTIVE"); employee.setStatus("UNAVAILABLE");
        }
        users.save(account); employees.save(employee);
        return toResponse(employee);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmployeeImportResponse importEmployees(UUID organizationId, EmployeeImportRequest request) {
        List<EmployeeImportRowResult> results = request.employees().stream().map(row -> {
            try {
                EmployeeResponse created = writer.create(organizationId, row);
                return EmployeeImportRowResult.created(row.rowNumber(), created);
            } catch (ApiException exception) {
                return EmployeeImportRowResult.skipped(row.rowNumber(), exception.getCode(), exception.getParams());
            } catch (DataIntegrityViolationException exception) {
                return EmployeeImportRowResult.skipped(row.rowNumber(), ErrorCode.EMAIL_ALREADY_IN_USE, java.util.Map.of());
            }
        }).toList();
        int created = (int) results.stream().filter(row -> row.status().equals("CREATED")).count();
        return new EmployeeImportResponse(created, results.size() - created, results);
    }

    private EmployeeResponse toResponse(HumanResource employee) {
        return new EmployeeResponse(employee.getId(), employee.getSpace().getId(), employee.getSpace().getName(),
                employee.getName(), employee.getEmail(), employee.getJobTitle(), employee.getStatus(),
                employee.getUser() != null, employee.getUser() != null && "ACTIVE".equals(employee.getUser().getStatus()),
                employee.getUser() == null ? null : employee.getUser().getCredentialEmailSentAt(), employee.getCreatedAt());
    }
}
