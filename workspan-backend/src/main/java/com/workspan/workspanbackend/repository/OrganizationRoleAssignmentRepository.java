package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.OrganizationRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.workspan.workspanbackend.entity.RoleScope;

import java.util.UUID;

public interface OrganizationRoleAssignmentRepository extends JpaRepository<OrganizationRoleAssignment, UUID> {
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("select count(a) > 0 from OrganizationRoleAssignment a where a.user.id = :userId and a.role.name = 'ADMIN' and a.role.scope = :scope")
    boolean hasOrganizationAdminRole(@Param("userId") UUID userId, @Param("scope") RoleScope scope);
}
