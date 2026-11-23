package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.ForbiddenException;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.*;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AccessControlService {
    private static final Set<String> RESERVED = Set.of("ADMIN", "EMPLOYEE", "SPACE_ADMIN", "PROJECT_ADMIN");
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final OrganizationRoleAssignmentRepository organizationAssignments;
    private final SpaceRoleAssignmentRepository spaceAssignments;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final UserAccountRepository users;
    private final SpaceRepository spaces;
    private final ProjectRepository projects;
    private final OrganizationRepository organizations;

    public AccessControlService(PermissionRepository permissions, RoleRepository roles, RolePermissionRepository rolePermissions,
                                OrganizationRoleAssignmentRepository organizationAssignments, SpaceRoleAssignmentRepository spaceAssignments,
                                ProjectRoleAssignmentRepository projectAssignments, UserAccountRepository users,
                                SpaceRepository spaces, ProjectRepository projects, OrganizationRepository organizations) {
        this.permissions = permissions; this.roles = roles; this.rolePermissions = rolePermissions;
        this.organizationAssignments = organizationAssignments; this.spaceAssignments = spaceAssignments;
        this.projectAssignments = projectAssignments; this.users = users; this.spaces = spaces; this.projects = projects; this.organizations = organizations;
    }

    public List<PermissionResponse> permissionCatalog() {
        return PermissionCatalog.DEFINITIONS.stream().map(def -> new PermissionResponse(def.key(), def.label(), def.description(), def.category(), def.action(), def.scopes())).toList();
    }

    public List<RoleResponse> roles(UUID organizationId, RoleScope scope, UUID contextId) {
        validateContext(organizationId, scope, contextId);
        return findRoles(organizationId, scope, contextId).stream().map(this::roleResponse).toList();
    }

    @Transactional
    public RoleResponse createRole(UUID organizationId, RoleRequest request) {
        String name = request.name().trim();
        rejectReserved(name);
        if (roles.findByOrganizationIdAndNameAndScope(organizationId, name, request.scope()).isPresent())
            throw new ConflictException(ErrorCode.ROLE_NAME_TAKEN);
        Organization organization = organizations.findById(organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
        Role role = new Role(); role.setOrganization(organization); role.setName(name); role.setScope(request.scope());
        setRoleContext(role, request.scope(), request.contextId(), organizationId);
        return saveRole(role, request.permissionKeys());
    }

    @Transactional
    public RoleResponse updateRole(UUID organizationId, UUID roleId, RoleRequest request) {
        Role role = roles.findById(roleId).filter(item -> item.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND));
        if (reserved(role.getName())) throw new ConflictException(ErrorCode.BUILT_IN_ROLE_IMMUTABLE);
        if (role.getScope() != request.scope() || !sameContext(role, request.scope(), request.contextId())) throw new BadRequestException(ErrorCode.ROLE_SCOPE_IMMUTABLE);
        rejectReserved(request.name().trim());
        if (!role.getName().equalsIgnoreCase(request.name().trim()) && roles.existsByOrganizationIdAndNameAndScopeAndIdNot(organizationId, request.name().trim(), role.getScope(), roleId))
            throw new ConflictException(ErrorCode.ROLE_NAME_TAKEN);
        role.setName(request.name().trim());
        return saveRole(role, request.permissionKeys());
    }

    @Transactional
    public void deleteRole(UUID organizationId, UUID roleId, RoleScope scope, UUID contextId) {
        Role role = roles.findById(roleId).filter(item -> item.getOrganization().getId().equals(organizationId) && item.getScope() == scope && sameContext(item, scope, contextId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND));
        if (reserved(role.getName())) throw new ConflictException(ErrorCode.BUILT_IN_ROLE_UNDELETABLE);
        if (organizationAssignments.existsByRoleId(roleId) || spaceAssignments.existsByRoleId(roleId) || projectAssignments.existsByRoleId(roleId))
            throw new ConflictException(ErrorCode.ROLE_IN_USE);
        rolePermissions.deleteAllByRoleId(roleId); roles.delete(role);
    }

    public List<AccessMemberResponse> members(UUID organizationId) {
        return users.findAllByOrganizationIdAndStatusOrderByDisplayNameAsc(organizationId, "ACTIVE").stream()
                .map(user -> new AccessMemberResponse(user.getId(), user.getDisplayName(), user.getEmail())).toList();
    }

    public List<RoleAssignmentResponse> assignments(UUID organizationId, RoleScope scope, UUID contextId) {
        validateContext(organizationId, scope, contextId);
        if (scope == RoleScope.ORGANIZATION) return organizationAssignments.findAllByRoleOrganizationIdAndRoleScopeOrderByUserDisplayNameAscRoleNameAsc(organizationId, scope)
                .stream().filter(a -> a.getRole().getScopeSpace() == null && a.getRole().getScopeProject() == null)
                .map(a -> new RoleAssignmentResponse(a.getUser().getId(), a.getUser().getDisplayName(), a.getUser().getEmail(), a.getRole().getId(), a.getRole().getName(), scope, a.getAssignedAt())).toList();
        if (scope == RoleScope.SPACE) return spaceAssignments.findAllBySpaceIdOrderByUserDisplayNameAscRoleNameAsc(contextId)
                .stream().filter(a -> a.getRole().getOrganization().getId().equals(organizationId) && a.getRole().getScope() == scope && sameContext(a.getRole(), scope, contextId))
                .map(a -> new RoleAssignmentResponse(a.getUser().getId(), a.getUser().getDisplayName(), a.getUser().getEmail(), a.getRole().getId(), a.getRole().getName(), scope, a.getAssignedAt())).toList();
        return projectAssignments.findAllByProjectIdOrderByUserDisplayNameAscRoleNameAsc(contextId)
                .stream().filter(a -> a.getRole().getOrganization().getId().equals(organizationId) && a.getRole().getScope() == scope && sameContext(a.getRole(), scope, contextId))
                .map(a -> new RoleAssignmentResponse(a.getUser().getId(), a.getUser().getDisplayName(), a.getUser().getEmail(), a.getRole().getId(), a.getRole().getName(), scope, a.getAssignedAt())).toList();
    }

    @Transactional
    public RoleAssignmentResponse assign(UUID organizationId, RoleScope scope, UUID contextId, UUID actorId, RoleAssignmentRequest request) {
        validateContext(organizationId, scope, contextId);
        if (actorId.equals(request.userId()))
            throw new ForbiddenException(ErrorCode.SELF_ROLE_ASSIGNMENT_FORBIDDEN);
        UserAccount user = users.findByIdAndOrganizationId(request.userId(), organizationId)
                .filter(item -> "ACTIVE".equals(item.getStatus())).orElseThrow(() -> new NotFoundException(ErrorCode.ACTIVE_MEMBER_NOT_FOUND));
        Role role = roles.findById(request.roleId()).filter(item -> item.getOrganization().getId().equals(organizationId) && item.getScope() == scope && sameContext(item, scope, contextId))
                .orElseThrow(() -> new BadRequestException(ErrorCode.ROLE_REQUIRED_FOR_SCOPE));
        if (role.getName().equalsIgnoreCase("ADMIN")) throw new BadRequestException(ErrorCode.SUPER_ADMIN_NOT_ASSIGNABLE);
        if (scope == RoleScope.ORGANIZATION) {
            if (!organizationAssignments.existsByUserIdAndRoleId(user.getId(), role.getId())) {
                OrganizationRoleAssignment assignment = new OrganizationRoleAssignment(); assignment.setUser(user); assignment.setRole(role);
                organizationAssignments.save(assignment);
            }
        } else if (scope == RoleScope.SPACE) {
            Space space = requireSpace(contextId, organizationId);
            if (spaceAssignments.findByUserIdAndRoleIdAndSpaceId(user.getId(), role.getId(), contextId).isEmpty()) {
                SpaceRoleAssignment assignment = new SpaceRoleAssignment(); assignment.setUser(user); assignment.setRole(role); assignment.setSpace(space);
                spaceAssignments.save(assignment);
            }
        } else {
            Project project = requireProject(contextId, organizationId);
            if (projectAssignments.findByUserIdAndRoleIdAndProjectId(user.getId(), role.getId(), contextId).isEmpty()) {
                ProjectRoleAssignment assignment = new ProjectRoleAssignment(); assignment.setUser(user); assignment.setRole(role); assignment.setProject(project);
                projectAssignments.save(assignment);
            }
        }
        return new RoleAssignmentResponse(user.getId(), user.getDisplayName(), user.getEmail(), role.getId(), role.getName(), scope, java.time.Instant.now());
    }

    @Transactional
    public void unassign(UUID organizationId, RoleScope scope, UUID contextId, UUID userId, UUID roleId) {
        validateContext(organizationId, scope, contextId);
        Role role = roles.findById(roleId).filter(item -> item.getOrganization().getId().equals(organizationId) && item.getScope() == scope && sameContext(item, scope, contextId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND));
        if (role.getName().equalsIgnoreCase("ADMIN")) throw new ConflictException(ErrorCode.SUPER_ADMIN_ASSIGNMENT_PROTECTED);
        if (scope == RoleScope.ORGANIZATION) organizationAssignments.findByUserIdAndRoleId(userId, roleId).ifPresent(organizationAssignments::delete);
        else if (scope == RoleScope.SPACE) spaceAssignments.findByUserIdAndRoleIdAndSpaceId(userId, roleId, contextId).ifPresent(spaceAssignments::delete);
        else projectAssignments.findByUserIdAndRoleIdAndProjectId(userId, roleId, contextId).ifPresent(projectAssignments::delete);
    }

    private RoleResponse saveRole(Role role, List<String> permissionKeys) {
        List<String> distinctKeys = permissionKeys.stream().distinct().toList();
        if (distinctKeys.stream().anyMatch(key -> !allowedAtScope(role.getScope(), key)))
            throw new BadRequestException(ErrorCode.PERMISSION_SCOPE_MISMATCH);
        if ((distinctKeys.contains("gantt.schedule.manage") || distinctKeys.contains("gantt.dependencies.manage")) && !distinctKeys.contains("gantt.view"))
            throw new BadRequestException(ErrorCode.GANTT_PERMISSIONS_REQUIRE_VIEW);
        if (distinctKeys.contains("workload.manage") && !distinctKeys.contains("workload.view"))
            throw new BadRequestException(ErrorCode.WORKLOAD_MANAGE_REQUIRES_VIEW);
        List<Permission> selected = permissions.findAllByKeyIn(distinctKeys);
        if (selected.size() != distinctKeys.size()) throw new BadRequestException(ErrorCode.UNKNOWN_PERMISSION);
        Role saved = roles.saveAndFlush(role);
        rolePermissions.deleteAllByRoleId(saved.getId());
        selected.forEach(permission -> { RolePermission link = new RolePermission(); link.setRole(saved); link.setPermission(permission); rolePermissions.save(link); });
        return new RoleResponse(saved.getId(), saved.getName(), saved.getScope(), contextId(saved), distinctKeys);
    }

    private RoleResponse roleResponse(Role role) {
        List<String> keys = rolePermissions.findAllByRoleId(role.getId()).stream().map(link -> link.getPermission().getKey()).sorted().toList();
        return new RoleResponse(role.getId(), role.getName(), role.getScope(), contextId(role), keys);
    }
    private void validateContext(UUID organizationId, RoleScope scope, UUID contextId) {
        if (scope == RoleScope.ORGANIZATION) {
            if (!organizationId.equals(contextId)) throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
        } else if (scope == RoleScope.SPACE) requireSpace(contextId, organizationId);
        else requireProject(contextId, organizationId);
    }
    private Space requireSpace(UUID id, UUID organizationId) {
        return spaces.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.SPACE_NOT_FOUND));
    }
    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private List<Role> findRoles(UUID organizationId, RoleScope scope, UUID contextId) {
        if (scope == RoleScope.ORGANIZATION) return roles.findAllByOrganizationIdAndScopeOrderByNameAsc(organizationId, scope).stream()
                .filter(role -> role.getScopeSpace() == null && role.getScopeProject() == null).toList();
        if (scope == RoleScope.SPACE) return roles.findAllByOrganizationIdAndScopeAndScopeSpaceIdOrderByNameAsc(organizationId, scope, contextId);
        return roles.findAllByOrganizationIdAndScopeAndScopeProjectIdOrderByNameAsc(organizationId, scope, contextId);
    }
    private void setRoleContext(Role role, RoleScope scope, UUID contextId, UUID organizationId) {
        if (scope == RoleScope.SPACE) role.setScopeSpace(requireSpace(contextId, organizationId));
        else if (scope == RoleScope.PROJECT) role.setScopeProject(requireProject(contextId, organizationId));
        else if (!organizationId.equals(contextId)) throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
    }
    private boolean sameContext(Role role, RoleScope scope, UUID contextId) {
        if (scope == RoleScope.ORGANIZATION) return role.getScopeSpace() == null && role.getScopeProject() == null;
        if (scope == RoleScope.SPACE) return role.getScopeSpace() != null && role.getScopeSpace().getId().equals(contextId) && role.getScopeProject() == null;
        return role.getScopeProject() != null && role.getScopeProject().getId().equals(contextId) && role.getScopeSpace() == null;
    }
    private UUID contextId(Role role) {
        if (role.getScopeSpace() != null) return role.getScopeSpace().getId();
        if (role.getScopeProject() != null) return role.getScopeProject().getId();
        return role.getOrganization().getId();
    }
    private boolean reserved(String name) { return RESERVED.contains(name.toUpperCase(Locale.ROOT)) || name.startsWith("SPACE_ADMIN - ") || name.startsWith("PROJECT_ADMIN - "); }
    private boolean allowedAtScope(RoleScope scope, String key) {
        return PermissionCatalog.allowedAt(scope, key);
    }
    private void rejectReserved(String name) { if (reserved(name)) throw new BadRequestException(ErrorCode.RESERVED_ROLE_NAME); }
}
