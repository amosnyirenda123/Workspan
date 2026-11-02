package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findAllByRoleId(UUID roleId);
    void deleteAllByRoleId(UUID roleId);
    boolean existsByRoleId(UUID roleId);
}
