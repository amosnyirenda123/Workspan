package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.HumanResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface HumanResourceRepository extends JpaRepository<HumanResource, UUID> {
    List<HumanResource> findAllBySpaceOrganizationIdOrderByNameAsc(UUID organizationId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
    Optional<HumanResource> findByIdAndSpaceOrganizationId(UUID id, UUID organizationId);
}
