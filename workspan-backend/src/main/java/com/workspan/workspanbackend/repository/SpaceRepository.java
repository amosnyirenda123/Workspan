package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    long countByOrganizationId(UUID organizationId);
    List<Space> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<Space> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
