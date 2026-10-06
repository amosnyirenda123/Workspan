package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    long countBySpaceOrganizationId(UUID organizationId);
}
