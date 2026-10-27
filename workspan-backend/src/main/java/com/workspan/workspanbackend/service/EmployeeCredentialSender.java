package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmployeeCredentialSender {
    private final HumanResourceRepository employees;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final OrganizationRoleAssignmentRepository assignments;
    private final PasswordHasher hasher;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String from;
    private final String frontendUrl;

    public EmployeeCredentialSender(HumanResourceRepository employees, UserAccountRepository users,
                                    RoleRepository roles, OrganizationRoleAssignmentRepository assignments,
                                    PasswordHasher hasher, JavaMailSender mailSender,
                                    @Value("${workspan.mail.from:no-reply@workspan.local}") String from,
                                    @Value("${workspan.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.employees = employees;
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.hasher = hasher;
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public CredentialDeliveryResult send(UUID organizationId, UUID employeeId, boolean forceResend) {
        HumanResource employee = employees.findByIdAndSpaceOrganizationId(employeeId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        Organization organization = employee.getSpace().getOrganization();
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee email is missing; add an email before sending credentials");
        }
        String email = employee.getEmail().trim().toLowerCase(Locale.ROOT);
        UserAccount user = employee.getUser();

        if (user != null && !forceResend && user.getCredentialEmailSentAt() != null) {
            return new CredentialDeliveryResult(employee.getId(), email, "SKIPPED", "Credentials were already sent", user.getCredentialEmailSentAt());
        }
        if (user == null) {
            if (users.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This email already belongs to another account");
            }
            user = new UserAccount();
            user.setOrganization(organization);
            user.setEmail(email);
            user.setDisplayName(employee.getName());
        } else if (!user.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee email does not match the linked account");
        }

        String temporaryPassword = generatePassword();
        user.setPasswordHash(hasher.hash(temporaryPassword));
        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);

        Role employeeRole = roles.findByOrganizationIdAndNameAndScope(organization.getId(), "EMPLOYEE", RoleScope.ORGANIZATION)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setOrganization(organization);
                    role.setName("EMPLOYEE");
                    role.setScope(RoleScope.ORGANIZATION);
                    return roles.save(role);
                });
        UserAccount savedUser = users.saveAndFlush(user);
        if (!assignments.existsByUserIdAndRoleId(savedUser.getId(), employeeRole.getId())) {
            OrganizationRoleAssignment assignment = new OrganizationRoleAssignment();
            assignment.setUser(savedUser);
            assignment.setRole(employeeRole);
            assignments.save(assignment);
        }
        employee.setUser(savedUser);

        sendMessage(email, employee.getName(), organization.getName(), temporaryPassword);
        Instant sentAt = Instant.now();
        savedUser.setCredentialEmailSentAt(sentAt);
        users.save(savedUser);
        return new CredentialDeliveryResult(employee.getId(), email, "SENT", "Temporary credentials sent", sentAt);
    }

    private String generatePassword() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendMessage(String email, String displayName, String organizationName, String password) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your Workspan account for " + organizationName);
        message.setText("Hello " + displayName + ",\n\n"
                + "Your Workspan account for " + organizationName + " is ready.\n\n"
                + "Email: " + email + "\n"
                + "Temporary password: " + password + "\n\n"
                + "Sign in at " + frontendUrl + "/login. You will be asked to choose a new password before continuing.\n\n"
                + "If you were not expecting this email, contact your organisation administrator.");
        mailSender.send(message);
    }
}
