package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.OrganizationRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRoleAssignmentRepository extends JpaRepository<OrganizationRoleAssignment, UUID> {}
