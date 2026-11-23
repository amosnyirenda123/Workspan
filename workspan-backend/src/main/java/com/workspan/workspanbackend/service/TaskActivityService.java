package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskActivityResponse;
import com.workspan.workspanbackend.entity.TaskActivity;
import com.workspan.workspanbackend.repository.TaskActivityRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskActivityService {
    private final TaskActivityRepository activities;
    private final TaskRepository tasks;
    private final UserAccountRepository users;
    public TaskActivityService(TaskActivityRepository activities, TaskRepository tasks, UserAccountRepository users) {
        this.activities = activities; this.tasks = tasks; this.users = users;
    }
    public List<TaskActivityResponse> list(UUID taskId, UUID organizationId) {
        if (tasks.findByIdAndProjectSpaceOrganizationId(taskId, organizationId).isEmpty()) throw new NotFoundException(ErrorCode.TASK_NOT_FOUND);
        return activities.findAllByTaskIdOrderByCreatedAtDesc(taskId).stream().map(this::response).toList();
    }
    @Transactional
    public void record(UUID taskId, UUID projectId, UUID actorId, String action, String summary) {
        TaskActivity activity = new TaskActivity(); activity.setTaskId(taskId); activity.setProjectId(projectId); activity.setActorId(actorId);
        activity.setActorName(users.findById(actorId).map(user -> user.getDisplayName()).orElse("Workspan"));
        activity.setAction(action); activity.setSummary(summary); activities.save(activity);
    }
    private TaskActivityResponse response(TaskActivity item) {
        return new TaskActivityResponse(item.getId(), item.getTaskId(), item.getProjectId(), item.getActorId(), item.getActorName(), item.getAction(), item.getSummary(), item.getCreatedAt());
    }
}
