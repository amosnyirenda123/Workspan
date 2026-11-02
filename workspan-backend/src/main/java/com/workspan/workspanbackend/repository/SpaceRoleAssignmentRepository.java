package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.SpaceRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRoleAssignmentRepository extends JpaRepository<SpaceRoleAssignment, UUID> {
    @Query("select count(a) > 0 from SpaceRoleAssignment a join RolePermission rp on rp.role = a.role where a.user.id = :userId and a.space.id = :spaceId and rp.permission.key = :permissionKey")
    boolean hasPermission(@Param("userId") UUID userId, @Param("spaceId") UUID spaceId, @Param("permissionKey") String permissionKey);
    List<SpaceRoleAssignment> findAllBySpaceIdOrderByUserDisplayNameAscRoleNameAsc(UUID spaceId);
    Optional<SpaceRoleAssignment> findByUserIdAndRoleIdAndSpaceId(UUID userId, UUID roleId, UUID spaceId);
    boolean existsByRoleId(UUID roleId);
}
