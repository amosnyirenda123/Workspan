package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
