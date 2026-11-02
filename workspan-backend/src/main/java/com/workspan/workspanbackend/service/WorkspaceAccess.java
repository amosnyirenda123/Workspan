package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.AuthResponse;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.repository.OrganizationRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.SpaceRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.ProjectRoleAssignmentRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.entity.Project;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;

@Component
public class WorkspaceAccess {
    private final AuthService auth;
    private final OrganizationRoleAssignmentRepository assignments;
    private final SpaceRoleAssignmentRepository spaceAssignments;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final SpaceRepository spaces;
    private final ProjectRepository projects;

    public WorkspaceAccess(AuthService auth, OrganizationRoleAssignmentRepository assignments,
                           SpaceRoleAssignmentRepository spaceAssignments, ProjectRoleAssignmentRepository projectAssignments,
                           SpaceRepository spaces, ProjectRepository projects) {
        this.auth = auth;
        this.assignments = assignments;
        this.spaceAssignments = spaceAssignments;
        this.projectAssignments = projectAssignments;
        this.spaces = spaces;
        this.projects = projects;
    }

    public UUID organizationId(HttpSession session) {
        return current(session).organizationId();
    }

    public UUID userId(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        AuthResponse user = auth.currentUser(userId);
        if (user.mustChangePassword()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Change the temporary password before continuing");
        return user.userId();
    }

    private AuthResponse current(HttpSession session) {
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof UUID userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        AuthResponse user = auth.currentUser(userId);
        if (user.mustChangePassword()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Change the temporary password before continuing");
        return user;
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

    public UUID requireOrganizationPermission(HttpSession session, String permissionKey) {
        AuthResponse user = current(session);
        require(user, RoleScope.ORGANIZATION, null, permissionKey);
        return user.organizationId();
    }

    public UUID requireSpacePermission(HttpSession session, UUID spaceId, String permissionKey) {
        AuthResponse user = current(session);
        Space space = spaces.findByIdAndOrganizationId(spaceId, user.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
        require(user, RoleScope.SPACE, spaceId, permissionKey);
        return user.organizationId();
    }

    public UUID requireSpaceAnyPermission(HttpSession session, UUID spaceId, List<String> permissionKeys) {
        AuthResponse user = current(session);
        Space space = spaces.findByIdAndOrganizationId(spaceId, user.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
        boolean allowed = permissionKeys.stream().anyMatch(key -> hasSpacePermission(user.userId(), user.organizationId(), spaceId, key));
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A space permission is required");
        return user.organizationId();
    }

    public UUID requireProjectPermission(HttpSession session, UUID projectId, String permissionKey) {
        AuthResponse user = current(session);
        Project project = projects.findByIdAndSpaceOrganizationId(projectId, user.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!hasProjectPermission(user.userId(), user.organizationId(), project.getSpace().getId(), projectId, permissionKey))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission required: " + permissionKey);
        return user.organizationId();
    }

    public UUID requireProjectAnyPermission(HttpSession session, UUID projectId, List<String> permissionKeys) {
        AuthResponse user = current(session);
        Project project = projects.findByIdAndSpaceOrganizationId(projectId, user.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        boolean allowed = permissionKeys.stream().anyMatch(key -> hasProjectPermission(user.userId(), user.organizationId(), project.getSpace().getId(), projectId, key));
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A project permission is required");
        return user.organizationId();
    }

    public boolean hasOrganizationPermission(UUID userId, UUID organizationId, String permissionKey) {
        return assignments.hasOrganizationAdminRole(userId, RoleScope.ORGANIZATION)
                || assignments.hasPermission(userId, organizationId, permissionKey);
    }

    public boolean isOrganizationSuperAdmin(UUID userId) {
        return assignments.hasOrganizationAdminRole(userId, RoleScope.ORGANIZATION);
    }

    public boolean hasSpacePermission(UUID userId, UUID organizationId, UUID spaceId, String permissionKey) {
        return hasOrganizationPermission(userId, organizationId, permissionKey)
                || spaceAssignments.hasPermission(userId, spaceId, permissionKey);
    }

    public boolean hasProjectPermission(UUID userId, UUID organizationId, UUID spaceId, UUID projectId, String permissionKey) {
        return hasSpacePermission(userId, organizationId, spaceId, permissionKey)
                || projectAssignments.hasPermission(userId, projectId, permissionKey);
    }

    public boolean hasProjectPermissionById(UUID userId, UUID organizationId, UUID projectId, String permissionKey) {
        return projects.findByIdAndSpaceOrganizationId(projectId, organizationId)
                .map(project -> hasProjectPermission(userId, organizationId, project.getSpace().getId(), projectId, permissionKey))
                .orElse(false);
    }

    public boolean canSeeSpace(UUID userId, UUID organizationId, UUID spaceId) {
        return List.of("space.view", "space.edit", "space.delete", "project.create", "employee.view", "employee.create", "employee.manage_accounts", "resource.pool.manage")
                .stream().anyMatch(key -> hasSpacePermission(userId, organizationId, spaceId, key))
                || projects.findAllBySpaceIdOrderByNameAsc(spaceId).stream()
                .anyMatch(project -> List.of("project.view", "project.edit", "project.delete", "project.resources.manage", "task.view", "task.create", "task.edit", "task.delete", "access.manage")
                        .stream().anyMatch(key -> hasProjectPermission(userId, organizationId, spaceId, project.getId(), key)));
    }

    public void requireScopeManager(HttpSession session, RoleScope scope, UUID scopeId) {
        AuthResponse user = current(session);
        if (scope == RoleScope.ORGANIZATION) {
            if (!user.organizationId().equals(scopeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
            require(user, RoleScope.ORGANIZATION, null, "access.manage");
        } else if (scope == RoleScope.SPACE) {
            Space space = spaces.findByIdAndOrganizationId(scopeId, user.organizationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
            require(user, RoleScope.SPACE, space.getId(), "access.manage");
        } else {
            Project project = projects.findByIdAndSpaceOrganizationId(scopeId, user.organizationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
            if (!hasProjectPermission(user.userId(), user.organizationId(), project.getSpace().getId(), project.getId(), "access.manage"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission required: access.manage");
        }
    }

    private void require(AuthResponse user, RoleScope targetScope, UUID scopeId, String permissionKey) {
        UUID userId = user.userId();
        if (assignments.hasOrganizationAdminRole(userId, RoleScope.ORGANIZATION)) return;
        boolean allowed = assignments.hasPermission(userId, user.organizationId(), permissionKey);
        if (!allowed && targetScope != RoleScope.ORGANIZATION && scopeId != null)
            allowed = targetScope == RoleScope.SPACE
                    ? spaceAssignments.hasPermission(userId, scopeId, permissionKey)
                    : projectAssignments.hasPermission(userId, scopeId, permissionKey);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission required: " + permissionKey);
    }
}
