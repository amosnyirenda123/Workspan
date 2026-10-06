package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    long countByProjectSpaceOrganizationId(UUID organizationId);
}
