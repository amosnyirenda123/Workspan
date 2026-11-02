package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByKeyAsc();
    Optional<Permission> findByKey(String key);
    List<Permission> findAllByKeyIn(List<String> keys);
}
