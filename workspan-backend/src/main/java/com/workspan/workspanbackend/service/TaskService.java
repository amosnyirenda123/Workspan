package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskRequest;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectRepository projects;

    public TaskService(TaskRepository tasks, ProjectRepository projects) { this.tasks = tasks; this.projects = projects; }

    public List<TaskResponse> findAll(UUID organizationId) {
        return tasks.findAllByProjectSpaceOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public TaskResponse findById(UUID id, UUID organizationId) { return response(requireTask(id, organizationId)); }

    @Transactional
    public TaskResponse create(UUID organizationId, TaskRequest request) {
        Project project = requireProject(request.projectId(), organizationId);
        validateDates(request.startDate(), request.endDate());
        Task task = new Task(project, request.name().trim());
        task.setStatus(request.status()); task.setStartDate(request.startDate()); task.setEndDate(request.endDate());
        return response(tasks.save(task));
    }

    @Transactional
    public TaskResponse update(UUID id, UUID organizationId, TaskRequest request) {
        Task task = requireTask(id, organizationId);
        task.setProject(requireProject(request.projectId(), organizationId));
        task.setName(request.name().trim()); task.setStatus(request.status());
        validateDates(request.startDate(), request.endDate());
        task.setStartDate(request.startDate()); task.setEndDate(request.endDate());
        return response(tasks.save(task));
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) { tasks.delete(requireTask(id, organizationId)); }

    private Task requireTask(UUID id, UUID organizationId) {
        return tasks.findByIdAndProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
    }

    private TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getName(), task.getStatus(), task.getStartDate(), task.getEndDate(), task.getCreatedAt());
    }
}
