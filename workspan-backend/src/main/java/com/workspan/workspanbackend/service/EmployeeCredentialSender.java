package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.entity.Organization;
import com.workspan.workspanbackend.entity.OrganizationRoleAssignment;
import com.workspan.workspanbackend.entity.Role;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.entity.SpaceRoleAssignment;
import com.workspan.workspanbackend.entity.UserAccount;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import com.workspan.workspanbackend.repository.OrganizationRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.RoleRepository;
import com.workspan.workspanbackend.repository.RolePermissionRepository;
import com.workspan.workspanbackend.repository.SpaceRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RolePermissionRepository rolePermissions;
    private final OrganizationRoleAssignmentRepository organizationAssignments;
    private final SpaceRoleAssignmentRepository spaceAssignments;
    private final PasswordHasher hasher;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String from;
    private final String frontendUrl;

    public EmployeeCredentialSender(HumanResourceRepository employees, UserAccountRepository users,
                                    RoleRepository roles, RolePermissionRepository rolePermissions,
                                    OrganizationRoleAssignmentRepository organizationAssignments,
                                    SpaceRoleAssignmentRepository spaceAssignments,
                                    PasswordHasher hasher, JavaMailSender mailSender,
                                    @Value("${workspan.mail.from:no-reply@workspan.local}") String from,
                                    @Value("${workspan.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.employees = employees;
        this.users = users;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.organizationAssignments = organizationAssignments;
        this.spaceAssignments = spaceAssignments;
        this.hasher = hasher;
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public CredentialDeliveryResult send(UUID organizationId, UUID employeeId, boolean forceResend) {
        return send(organizationId, employeeId, forceResend, null, null);
    }

    @Transactional
    public CredentialDeliveryResult send(UUID organizationId, UUID employeeId, boolean forceResend, UUID roleId, RoleScope roleScope) {
        HumanResource employee = employees.findByIdAndSpaceOrganizationId(employeeId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND));
        Organization organization = employee.getSpace().getOrganization();
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new BadRequestException(ErrorCode.EMPLOYEE_EMAIL_MISSING);
        }
        String email = employee.getEmail().trim().toLowerCase(Locale.ROOT);
        UserAccount user = employee.getUser();

        if (user != null && !forceResend && user.getCredentialEmailSentAt() != null) {
            return CredentialDeliveryResult.skipped(employee.getId(), email, ErrorCode.CREDENTIALS_ALREADY_SENT, user.getCredentialEmailSentAt());
        }
        if (user == null && roleId == null && !forceResend) {
            return CredentialDeliveryResult.skipped(employee.getId(), email, ErrorCode.INVITATION_REQUIRED, null);
        }
        if (user == null) {
            if (roleId == null || roleScope == null)
                throw new BadRequestException(ErrorCode.INVITATION_ROLE_REQUIRED);
            if (users.existsByEmailIgnoreCase(email)) {
                throw new ConflictException(ErrorCode.EMAIL_ALREADY_IN_USE);
            }
            user = new UserAccount();
            user.setOrganization(organization);
            user.setEmail(email);
            user.setDisplayName(employee.getName());
        } else if (roleId != null || roleScope != null) {
            throw new ConflictException(ErrorCode.EMPLOYEE_ALREADY_HAS_ACCOUNT);
        } else if (!user.getEmail().equalsIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMPLOYEE_EMAIL_ACCOUNT_MISMATCH);
        }

        String temporaryPassword = generatePassword();
        user.setPasswordHash(hasher.hash(temporaryPassword));
        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);

        UserAccount savedUser = users.saveAndFlush(user);
        employee.setUser(savedUser);
        if (roleId != null) assignRole(organizationId, employee, savedUser, roleId, roleScope);

        sendMessage(email, employee.getName(), organization.getName(), temporaryPassword);
        Instant sentAt = Instant.now();
        savedUser.setCredentialEmailSentAt(sentAt);
        users.save(savedUser);
        return CredentialDeliveryResult.sent(employee.getId(), email, sentAt);
    }

    private void assignRole(UUID organizationId, HumanResource employee, UserAccount user, UUID roleId, RoleScope scope) {
        Role role = roles.findById(roleId)
                .filter(item -> item.getOrganization().getId().equals(organizationId) && item.getScope() == scope)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVITATION_ROLE_UNAVAILABLE));
        String roleName = role.getName().toUpperCase(Locale.ROOT);
        if (roleName.equals("ADMIN") || roleName.equals("EMPLOYEE") || roleName.startsWith("SPACE_ADMIN - ") || roleName.startsWith("PROJECT_ADMIN - "))
            throw new BadRequestException(ErrorCode.INVITATION_ROLE_NOT_CUSTOM);
        if (!rolePermissions.existsByRoleId(role.getId()))
            throw new BadRequestException(ErrorCode.INVITATION_ROLE_EMPTY);

        if (scope == RoleScope.ORGANIZATION) {
            if (role.getScopeSpace() != null || role.getScopeProject() != null)
                throw new BadRequestException(ErrorCode.INVITATION_ORGANIZATION_ROLE_REQUIRED);
            if (!organizationAssignments.existsByUserIdAndRoleId(user.getId(), role.getId())) {
                OrganizationRoleAssignment assignment = new OrganizationRoleAssignment();
                assignment.setUser(user);
                assignment.setRole(role);
                organizationAssignments.save(assignment);
            }
        } else if (scope == RoleScope.SPACE) {
            if (role.getScopeSpace() == null || !role.getScopeSpace().getId().equals(employee.getSpace().getId()) || role.getScopeProject() != null)
                throw new BadRequestException(ErrorCode.INVITATION_SPACE_ROLE_REQUIRED);
            if (spaceAssignments.findByUserIdAndRoleIdAndSpaceId(user.getId(), role.getId(), employee.getSpace().getId()).isEmpty()) {
                SpaceRoleAssignment assignment = new SpaceRoleAssignment();
                assignment.setUser(user);
                assignment.setRole(role);
                assignment.setSpace(employee.getSpace());
                spaceAssignments.save(assignment);
            }
        } else {
            throw new BadRequestException(ErrorCode.INVITATION_PROJECT_ROLE_FORBIDDEN);
        }
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
