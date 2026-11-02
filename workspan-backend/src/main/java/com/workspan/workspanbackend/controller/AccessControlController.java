package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.*;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.service.AccessControlService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import com.workspan.workspanbackend.repository.SpaceRepository;
import com.workspan.workspanbackend.repository.ProjectRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/access")
public class AccessControlController {
    private final AccessControlService accessControl;
    private final WorkspaceAccess access;
    private final SpaceRepository spaces;
    private final ProjectRepository projects;
    public AccessControlController(AccessControlService accessControl, WorkspaceAccess access, SpaceRepository spaces, ProjectRepository projects) { this.accessControl = accessControl; this.access = access; this.spaces = spaces; this.projects = projects; }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions(HttpSession session) { access.organizationId(session); return accessControl.permissionCatalog(); }

    @GetMapping("/management-context")
    public AccessManagementContextResponse managementContext(HttpSession session) {
        UUID userId = access.userId(session); UUID organizationId = access.organizationId(session);
        boolean orgManager = access.hasOrganizationPermission(userId, organizationId, "access.manage");
        List<AccessScopeOption> managedSpaces = spaces.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(space -> access.hasSpacePermission(userId, organizationId, space.getId(), "access.manage"))
                .map(space -> new AccessScopeOption(space.getId(), space.getName(), null)).toList();
        List<AccessScopeOption> managedProjects = projects.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(project -> access.hasProjectPermission(userId, organizationId, project.getSpace().getId(), project.getId(), "access.manage"))
                .map(project -> new AccessScopeOption(project.getId(), project.getName(), project.getSpace().getName())).toList();
        return new AccessManagementContextResponse(organizationId, orgManager, managedSpaces, managedProjects);
    }

    @GetMapping("/effective")
    public EffectiveAccessResponse effective(HttpSession session) {
        UUID userId = access.userId(session); UUID organizationId = access.organizationId(session);
        List<String> keys = accessControl.permissionCatalog().stream().map(PermissionResponse::key).toList();
        List<String> orgKeys = keys.stream().filter(key -> access.hasOrganizationPermission(userId, organizationId, key)).toList();
        java.util.Map<UUID, List<String>> spaceKeys = spaces.findAllByOrganizationIdOrderByNameAsc(organizationId).stream().collect(java.util.stream.Collectors.toMap(
                com.workspan.workspanbackend.entity.Space::getId,
                space -> keys.stream().filter(key -> access.hasSpacePermission(userId, organizationId, space.getId(), key)).toList()));
        java.util.Map<UUID, List<String>> projectKeys = projects.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId).stream().collect(java.util.stream.Collectors.toMap(
                com.workspan.workspanbackend.entity.Project::getId,
                project -> keys.stream().filter(key -> access.hasProjectPermission(userId, organizationId, project.getSpace().getId(), project.getId(), key)).toList()));
        boolean superAdmin = access.isOrganizationSuperAdmin(userId);
        return new EffectiveAccessResponse(userId, organizationId, superAdmin, orgKeys, spaceKeys, projectKeys);
    }

    @GetMapping("/members")
    public List<AccessMemberResponse> members(@RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        return accessControl.members(orgId);
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles(@RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        return accessControl.roles(orgId, scope, contextId);
    }

    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody RoleRequest request, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, request.scope(), request.contextId());
        return accessControl.createRole(orgId, request);
    }

    @PutMapping("/roles/{roleId}")
    public RoleResponse updateRole(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, request.scope(), request.contextId());
        return accessControl.updateRole(orgId, roleId, request);
    }

    @DeleteMapping("/roles/{roleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID roleId, @RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        accessControl.deleteRole(orgId, roleId, scope, contextId);
    }

    @GetMapping("/assignments")
    public List<RoleAssignmentResponse> assignments(@RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        return accessControl.assignments(orgId, scope, contextId);
    }

    @PostMapping("/assignments") @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentResponse assign(@RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId,
            @Valid @RequestBody RoleAssignmentRequest request, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        return accessControl.assign(orgId, scope, contextId, request);
    }

    @DeleteMapping("/assignments") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@RequestParam("scope") RoleScope scope, @RequestParam("contextId") UUID contextId,
            @RequestParam("userId") UUID userId, @RequestParam("roleId") UUID roleId, HttpSession session) {
        UUID orgId = access.organizationId(session); access.requireScopeManager(session, scope, contextId);
        accessControl.unassign(orgId, scope, contextId, userId, roleId);
    }
}
