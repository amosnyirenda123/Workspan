package com.workspan.workspanbackend.repository;
import com.workspan.workspanbackend.entity.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {
    List<TaskActivity> findAllByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
