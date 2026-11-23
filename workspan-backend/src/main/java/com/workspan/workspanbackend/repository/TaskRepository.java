package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    long countByProjectSpaceOrganizationId(UUID organizationId);
    List<Task> findAllByProjectSpaceOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<Task> findByIdAndProjectSpaceOrganizationId(UUID id, UUID organizationId);
    List<Task> findAllByProjectIdOrderByNameAsc(UUID projectId);
    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);
    long countBySprintId(UUID sprintId);
    long countBySprintIdAndStatus(UUID sprintId, com.workspan.workspanbackend.entity.TaskStatus status);
    List<Task> findAllBySprintId(UUID sprintId);
}
