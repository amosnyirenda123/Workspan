package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Sprint;
import com.workspan.workspanbackend.entity.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    List<Sprint> findAllByProjectIdOrderByStartDateDescNameAsc(UUID projectId);
    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
    boolean existsByProjectIdAndStatus(UUID projectId, SprintStatus status);
}
