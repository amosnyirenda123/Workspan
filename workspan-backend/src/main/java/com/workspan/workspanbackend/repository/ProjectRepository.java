package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    long countBySpaceOrganizationId(UUID organizationId);
    List<Project> findAllBySpaceOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<Project> findByIdAndSpaceOrganizationId(UUID id, UUID organizationId);
}
