package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskCommentRequest;
import com.workspan.workspanbackend.dto.TaskCommentResponse;
import com.workspan.workspanbackend.entity.TaskComment;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.repository.TaskCommentRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskCommentService {
    private final TaskCommentRepository comments;
    private final TaskRepository tasks;
    private final UserAccountRepository users;
    private final TaskActivityService activity;
    public TaskCommentService(TaskCommentRepository comments, TaskRepository tasks, UserAccountRepository users, TaskActivityService activity) {
        this.comments = comments; this.tasks = tasks; this.users = users; this.activity = activity;
    }
    public List<TaskCommentResponse> list(UUID taskId, UUID organizationId) {
        if (tasks.findByIdAndProjectSpaceOrganizationId(taskId, organizationId).isEmpty()) throw new NotFoundException(ErrorCode.TASK_NOT_FOUND);
        return comments.findAllByTaskIdOrderByCreatedAtAsc(taskId).stream().map(this::response).toList();
    }
    @Transactional
    public TaskCommentResponse create(UUID taskId, UUID organizationId, UUID authorId, TaskCommentRequest request) {
        var task = tasks.findByIdAndProjectSpaceOrganizationId(taskId, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.TASK_NOT_FOUND));
        var author = users.findByIdAndOrganizationId(authorId, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.ACTIVE_MEMBER_NOT_FOUND));
        TaskComment comment = new TaskComment(); comment.setTask(task); comment.setAuthor(author); comment.setBody(request.body().trim());
        TaskComment saved = comments.save(comment);
        activity.record(taskId, task.getProject().getId(), authorId, "COMMENTED", "Added a discussion comment");
        return response(saved);
    }
    private TaskCommentResponse response(TaskComment item) {
        return new TaskCommentResponse(item.getId(), item.getTask().getId(), item.getAuthor().getId(), item.getAuthor().getDisplayName(), item.getBody(), item.getCreatedAt());
    }
}
