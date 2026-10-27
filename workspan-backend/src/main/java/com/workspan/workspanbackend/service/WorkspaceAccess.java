package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.AuthResponse;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.repository.OrganizationRoleAssignmentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class WorkspaceAccess {
    private final AuthService auth;
    private final OrganizationRoleAssignmentRepository assignments;

    public WorkspaceAccess(AuthService auth, OrganizationRoleAssignmentRepository assignments) {
        this.auth = auth;
        this.assignments = assignments;
    }

    public UUID organizationId(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        AuthResponse user = auth.currentUser(userId);
        if (user.mustChangePassword()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Change the temporary password before continuing");
        return user.organizationId();
    }

    public UUID organizationAdminId(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        AuthResponse user = auth.currentUser(userId);
        if (user.mustChangePassword()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Change the temporary password before continuing");
        if (!assignments.hasOrganizationAdminRole(userId, RoleScope.ORGANIZATION)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization administrator access required");
        }
        return user.organizationId();
    }
}
