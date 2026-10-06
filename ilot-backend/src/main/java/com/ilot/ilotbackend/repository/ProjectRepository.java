package com.ilot.ilotbackend.repository;

import com.ilot.ilotbackend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    long countBySpaceOrganizationId(UUID organizationId);
}
