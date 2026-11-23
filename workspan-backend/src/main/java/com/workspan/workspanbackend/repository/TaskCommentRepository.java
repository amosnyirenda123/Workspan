package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(UUID taskId);
    long countByTaskId(UUID taskId);
    void deleteAllByTaskId(UUID taskId);
}
