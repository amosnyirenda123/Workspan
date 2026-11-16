package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Component
public class ScopedRoleProvisioner {
    public static final String SPACE_ADMIN_PREFIX = "SPACE_ADMIN - ";
    public static final String PROJECT_ADMIN_PREFIX = "PROJECT_ADMIN - ";

    /** Permissions granted to the creator of a space. */
    public static final List<String> SPACE_ADMIN_KEYS = List.of("space.view", "space.edit", "space.delete", "employee.view", "employee.create", "employee.manage_accounts",
            "resource.pool.view", "resource.pool.manage", "analytics.view", "project.view", "project.create", "project.edit", "project.delete",
            "project.resources.manage", "task.view", "task.view.kanban", "task.view.scrum", "task.create", "task.edit", "task.delete",
            "gantt.view", "gantt.dependencies.manage", "gantt.schedule.manage", "workload.view", "workload.manage", "access.manage");

    /** Permissions granted to the creator of a project. */
    public static final List<String> PROJECT_ADMIN_KEYS = List.of("project.view", "project.edit", "project.delete", "project.resources.manage", "analytics.view",
            "task.view", "task.view.kanban", "task.view.scrum", "task.create", "task.edit", "task.delete",
            "gantt.view", "gantt.dependencies.manage", "gantt.schedule.manage", "workload.view", "workload.manage", "access.manage");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final SpaceRoleAssignmentRepository spaceAssignments;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final UserAccountRepository users;

    public ScopedRoleProvisioner(RoleRepository roles, PermissionRepository permissions, RolePermissionRepository rolePermissions,
                                 SpaceRoleAssignmentRepository spaceAssignments, ProjectRoleAssignmentRepository projectAssignments,
                                 UserAccountRepository users) {
        this.roles = roles; this.permissions = permissions; this.rolePermissions = rolePermissions;
        this.spaceAssignments = spaceAssignments; this.projectAssignments = projectAssignments; this.users = users;
    }

    @Transactional
    public void assignSpaceOwner(UUID organizationId, UUID userId, Space space) {
        String roleName = SPACE_ADMIN_PREFIX + space.getId().toString().substring(0, 8);
        Role role = ensureRole(space.getOrganization(), roleName, RoleScope.SPACE, space, null,
                SPACE_ADMIN_KEYS);
        UserAccount user = users.findByIdAndOrganizationId(userId, organizationId).orElseThrow();
        if (spaceAssignments.findByUserIdAndRoleIdAndSpaceId(userId, role.getId(), space.getId()).isEmpty()) {
            SpaceRoleAssignment assignment = new SpaceRoleAssignment(); assignment.setUser(user); assignment.setRole(role); assignment.setSpace(space);
            spaceAssignments.save(assignment);
        }
    }

    @Transactional
    public void assignProjectOwner(UUID organizationId, UUID userId, Project project) {
        String roleName = PROJECT_ADMIN_PREFIX + project.getId().toString().substring(0, 8);
        Role role = ensureRole(project.getSpace().getOrganization(), roleName, RoleScope.PROJECT, null, project,
                PROJECT_ADMIN_KEYS);
        UserAccount user = users.findByIdAndOrganizationId(userId, organizationId).orElseThrow();
        if (projectAssignments.findByUserIdAndRoleIdAndProjectId(userId, role.getId(), project.getId()).isEmpty()) {
            ProjectRoleAssignment assignment = new ProjectRoleAssignment(); assignment.setUser(user); assignment.setRole(role); assignment.setProject(project);
            projectAssignments.save(assignment);
        }
    }

    /**
     * Brings built-in scoped admin roles up to date with the current catalogue (e.g. when new permissions such as
     * the Gantt ones are introduced), so existing space/project owners keep full control of their scope.
     */
    @Transactional
    public void syncBuiltInScopedRoles() {
        List<Permission> spaceKeys = permissions.findAllByKeyIn(SPACE_ADMIN_KEYS);
        List<Permission> projectKeys = permissions.findAllByKeyIn(PROJECT_ADMIN_KEYS);
        for (Role role : roles.findAllByNameStartingWith(SPACE_ADMIN_PREFIX))
            if (role.getScope() == RoleScope.SPACE) grantMissing(role, spaceKeys);
        for (Role role : roles.findAllByNameStartingWith(PROJECT_ADMIN_PREFIX))
            if (role.getScope() == RoleScope.PROJECT) grantMissing(role, projectKeys);
    }

    private void grantMissing(Role role, List<Permission> selected) {
        java.util.Set<UUID> currentPermissionIds = rolePermissions.findAllByRoleId(role.getId()).stream()
                .map(link -> link.getPermission().getId()).collect(java.util.stream.Collectors.toSet());
        for (Permission permission : selected) if (!currentPermissionIds.contains(permission.getId())) {
            RolePermission link = new RolePermission(); link.setRole(role); link.setPermission(permission); rolePermissions.save(link);
        }
    }

    private Role ensureRole(Organization organization, String name, RoleScope scope, Space space, Project project, List<String> keys) {
        UUID organizationId = organization.getId();
        java.util.Optional<Role> existing = scope == RoleScope.SPACE
                ? roles.findByOrganizationIdAndNameAndScopeAndScopeSpaceId(organizationId, name, scope, space.getId())
                : roles.findByOrganizationIdAndNameAndScopeAndScopeProjectId(organizationId, name, scope, project.getId());
        Role role = existing.orElseGet(() -> {
            Role created = new Role(); created.setOrganization(organization);
            created.setName(name); created.setScope(scope); created.setScopeSpace(space); created.setScopeProject(project); return roles.save(created);
        });
        java.util.Set<UUID> currentPermissionIds = rolePermissions.findAllByRoleId(role.getId()).stream()
                .map(link -> link.getPermission().getId()).collect(java.util.stream.Collectors.toSet());
        List<Permission> selected = permissions.findAllByKeyIn(keys);
        for (Permission permission : selected) if (!currentPermissionIds.contains(permission.getId())) {
            RolePermission link = new RolePermission(); link.setRole(role); link.setPermission(permission); rolePermissions.save(link);
        }
        return role;
    }
}
