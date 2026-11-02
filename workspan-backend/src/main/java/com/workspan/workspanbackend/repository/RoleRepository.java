package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Role;
import com.workspan.workspanbackend.entity.RoleScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByOrganizationIdAndNameAndScope(UUID organizationId, String name, RoleScope scope);
    boolean existsByOrganizationIdAndNameAndScopeAndIdNot(UUID organizationId, String name, RoleScope scope, UUID id);
    List<Role> findAllByOrganizationIdAndScopeOrderByNameAsc(UUID organizationId, RoleScope scope);
    List<Role> findAllByOrganizationIdAndScopeAndScopeSpaceIdOrderByNameAsc(UUID organizationId, RoleScope scope, UUID spaceId);
    List<Role> findAllByOrganizationIdAndScopeAndScopeProjectIdOrderByNameAsc(UUID organizationId, RoleScope scope, UUID projectId);
    Optional<Role> findByOrganizationIdAndNameAndScopeAndScopeSpaceId(UUID organizationId, String name, RoleScope scope, UUID spaceId);
    Optional<Role> findByOrganizationIdAndNameAndScopeAndScopeProjectId(UUID organizationId, String name, RoleScope scope, UUID projectId);
}
