package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.EmployeeRequest;
import com.workspan.workspanbackend.dto.EmployeeEmailRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRequest;
import com.workspan.workspanbackend.dto.EmployeeImportRow;
import com.workspan.workspanbackend.dto.EmployeeImportRowResult;
import com.workspan.workspanbackend.dto.EmployeeImportResponse;
import com.workspan.workspanbackend.dto.EmployeeResponse;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeService {
    private final HumanResourceRepository employees;
    private final EmployeeWriter writer;

    public EmployeeService(HumanResourceRepository employees, EmployeeWriter writer) {
        this.employees = employees;
        this.writer = writer;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmployeeImportResponse importEmployees(UUID organizationId, EmployeeImportRequest request) {
        List<EmployeeImportRowResult> results = request.employees().stream().map(row -> {
            try {
                EmployeeResponse created = writer.create(organizationId, row);
                return new EmployeeImportRowResult(row.rowNumber(), "CREATED", "Employee created", created);
            } catch (ResponseStatusException exception) {
                return new EmployeeImportRowResult(row.rowNumber(), "SKIPPED", exception.getReason(), null);
            } catch (DataIntegrityViolationException exception) {
                return new EmployeeImportRowResult(row.rowNumber(), "SKIPPED", "An employee with this email already exists", null);
            }
        }).toList();
        int created = (int) results.stream().filter(row -> row.status().equals("CREATED")).count();
        return new EmployeeImportResponse(created, results.size() - created, results);
    }

    private EmployeeResponse toResponse(HumanResource employee) {
        return new EmployeeResponse(employee.getId(), employee.getSpace().getId(), employee.getSpace().getName(),
                employee.getName(), employee.getEmail(), employee.getJobTitle(), employee.getStatus(),
                employee.getUser() != null,
                employee.getUser() == null ? null : employee.getUser().getCredentialEmailSentAt(), employee.getCreatedAt());
    }
}
