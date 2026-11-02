package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Role;
import com.workspan.workspanbackend.entity.RoleScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByOrganizationIdAndNameAndScope(UUID organizationId, String name, RoleScope scope);
}
