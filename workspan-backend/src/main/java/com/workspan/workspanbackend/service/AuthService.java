package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.AuthResponse;
import com.workspan.workspanbackend.dto.LoginRequest;
import com.workspan.workspanbackend.dto.RegisterRequest;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.OrganizationRepository;
import com.workspan.workspanbackend.repository.OrganizationRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.RoleRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final OrganizationRoleAssignmentRepository assignments;
    private final PasswordHasher hasher;

    public AuthService(OrganizationRepository organizations, UserAccountRepository users, RoleRepository roles,
                       OrganizationRoleAssignmentRepository assignments, PasswordHasher hasher) {
        this.organizations = organizations; this.users = users; this.roles = roles;
        this.assignments = assignments; this.hasher = hasher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String organizationName = request.organizationName().trim();
        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        if (organizations.existsByNameIgnoreCase(organizationName)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization with this name already exists");

        Organization organization = organizations.save(new Organization(organizationName));
        UserAccount user = new UserAccount();
        user.setOrganization(organization);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setPasswordHash(hasher.hash(request.password()));
        user.setStatus("ACTIVE");
        user = users.save(user);

        Role admin = new Role();
        admin.setOrganization(organization);
        admin.setName("ADMIN");
        admin.setScope(RoleScope.ORGANIZATION);
        admin = roles.save(admin);
        OrganizationRoleAssignment assignment = new OrganizationRoleAssignment();
        assignment.setUser(user);
        assignment.setRole(admin);
        assignments.save(assignment);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(account -> account.getStatus().equals("ACTIVE") && hasher.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse currentUser(UUID id) {
        return users.findById(id).filter(user -> user.getStatus().equals("ACTIVE"))
                .map(this::response).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private AuthResponse response(UserAccount user) {
        return new AuthResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getOrganization().getId(), user.getOrganization().getName());
    }
}
