package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.EmployeeImportRow;
import com.workspan.workspanbackend.dto.EmployeeEmailRequest;
import com.workspan.workspanbackend.dto.EmployeeResponse;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeWriter {
    private final HumanResourceRepository employees;
    private final SpaceRepository spaces;
    private final UserAccountRepository users;
    private final Validator validator;

    public EmployeeWriter(HumanResourceRepository employees, SpaceRepository spaces,
                          UserAccountRepository users, Validator validator) {
        this.employees = employees;
        this.spaces = spaces;
        this.users = users;
        this.validator = validator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmployeeResponse create(UUID organizationId, EmployeeImportRow row) {
        var violations = validator.validate(row);
        if (!violations.isEmpty()) {
            String message = violations.stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        String email = row.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (employees.existsByEmailIgnoreCase(email) || users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employee or user with this email already exists");
        }
        Space space = spaces.findByIdAndOrganizationId(row.spaceId(), organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Space not found in this organization"));

        HumanResource employee = new HumanResource();
        employee.setSpace(space);
        employee.setName(row.name().trim());
        employee.setEmail(email);
        employee.setJobTitle(row.jobTitle() == null || row.jobTitle().isBlank() ? null : row.jobTitle().trim());
        employee.setStatus("AVAILABLE");
        HumanResource saved = employees.saveAndFlush(employee);
        return new EmployeeResponse(saved.getId(), space.getId(), space.getName(), saved.getName(),
                saved.getEmail(), saved.getJobTitle(), saved.getStatus(), saved.getUser() != null,
                saved.getUser() == null ? null : saved.getUser().getCredentialEmailSentAt(), saved.getCreatedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmployeeResponse updateEmail(UUID organizationId, UUID employeeId, EmployeeEmailRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid employee email is required");
        HumanResource employee = employees.findByIdAndSpaceOrganizationId(employeeId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (employee.getUser() != null && !employee.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email cannot be changed after an account has been created");
        }
        if (employees.existsByEmailIgnoreCaseAndIdNot(email, employeeId) || users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email already belongs to another employee or account");
        }
        employee.setEmail(email);
        HumanResource saved = employees.saveAndFlush(employee);
        return new EmployeeResponse(saved.getId(), saved.getSpace().getId(), saved.getSpace().getName(), saved.getName(),
                saved.getEmail(), saved.getJobTitle(), saved.getStatus(), saved.getUser() != null,
                saved.getUser() == null ? null : saved.getUser().getCredentialEmailSentAt(), saved.getCreatedAt());
    }
}
