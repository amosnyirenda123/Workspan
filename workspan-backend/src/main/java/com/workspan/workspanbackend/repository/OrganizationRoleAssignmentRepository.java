package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.OrganizationRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.workspan.workspanbackend.entity.RoleScope;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface OrganizationRoleAssignmentRepository extends JpaRepository<OrganizationRoleAssignment, UUID> {
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("select count(a) > 0 from OrganizationRoleAssignment a where a.user.id = :userId and a.role.name = 'ADMIN' and a.role.scope = :scope")
    boolean hasOrganizationAdminRole(@Param("userId") UUID userId, @Param("scope") RoleScope scope);

    @Query("select count(a) > 0 from OrganizationRoleAssignment a join RolePermission rp on rp.role = a.role where a.user.id = :userId and a.role.organization.id = :organizationId and a.role.scope = com.workspan.workspanbackend.entity.RoleScope.ORGANIZATION and rp.permission.key = :permissionKey")
    boolean hasPermission(@Param("userId") UUID userId, @Param("organizationId") UUID organizationId, @Param("permissionKey") String permissionKey);

    List<OrganizationRoleAssignment> findAllByRoleOrganizationIdAndRoleScope(UUID organizationId, RoleScope scope);
    List<OrganizationRoleAssignment> findAllByRoleOrganizationIdAndRoleScopeOrderByUserDisplayNameAscRoleNameAsc(UUID organizationId, RoleScope scope);
    Optional<OrganizationRoleAssignment> findByUserIdAndRoleId(UUID userId, UUID roleId);
    boolean existsByRoleId(UUID roleId);
}
