package com.ilot.ilotbackend.repository;
import com.ilot.ilotbackend.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TaskRepository extends JpaRepository<Task, UUID> { long countByProjectSpaceOrganizationId(UUID organizationId); }
