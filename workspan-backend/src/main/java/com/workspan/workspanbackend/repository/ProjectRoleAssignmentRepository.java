package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.ProjectRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRoleAssignmentRepository extends JpaRepository<ProjectRoleAssignment, UUID> {
    @Query("select count(a) > 0 from ProjectRoleAssignment a join RolePermission rp on rp.role = a.role where a.user.id = :userId and a.project.id = :projectId and rp.permission.key = :permissionKey")
    boolean hasPermission(@Param("userId") UUID userId, @Param("projectId") UUID projectId, @Param("permissionKey") String permissionKey);
    List<ProjectRoleAssignment> findAllByProjectIdOrderByUserDisplayNameAscRoleNameAsc(UUID projectId);
    Optional<ProjectRoleAssignment> findByUserIdAndRoleIdAndProjectId(UUID userId, UUID roleId, UUID projectId);
    boolean existsByRoleId(UUID roleId);
}
