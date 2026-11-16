package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {
    List<TaskDependency> findAllByPredecessorTaskProjectId(UUID projectId);
    Optional<TaskDependency> findByIdAndPredecessorTaskProjectSpaceOrganizationId(UUID id, UUID organizationId);
    boolean existsByPredecessorTaskIdAndSuccessorTaskId(UUID predecessorId, UUID successorId);
    boolean existsByPredecessorTaskIdOrSuccessorTaskId(UUID predecessorId, UUID successorId);

    @Modifying
    @Query("delete from TaskDependency d where d.predecessorTask.id = :taskId or d.successorTask.id = :taskId")
    void deleteAllInvolvingTask(@Param("taskId") UUID taskId);
}
