package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.TaskResourceAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskResourceAllocationRepository extends JpaRepository<TaskResourceAllocation, UUID> {
    List<TaskResourceAllocation> findAllByTaskIdOrderByAllocatedAtAsc(UUID taskId);
    List<TaskResourceAllocation> findAllByTaskId(UUID taskId);
    List<TaskResourceAllocation> findAllByProjectResourceAllocationId(UUID allocationId);
    Optional<TaskResourceAllocation> findByTaskIdAndProjectResourceAllocationId(UUID taskId, UUID allocationId);
    boolean existsByProjectResourceAllocationId(UUID allocationId);
    void deleteAllByTaskId(UUID taskId);
}
