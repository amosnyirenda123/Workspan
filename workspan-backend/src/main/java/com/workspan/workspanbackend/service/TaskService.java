package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskRequest;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.BudgetResource;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
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
    private final TaskResourceAllocationRepository taskResourceAllocations;
    private final TaskResourceService taskResourceService;

    public TaskService(TaskRepository tasks, ProjectRepository projects, TaskResourceAllocationRepository taskResourceAllocations, TaskResourceService taskResourceService) { this.tasks = tasks; this.projects = projects; this.taskResourceAllocations = taskResourceAllocations; this.taskResourceService = taskResourceService; }

    public List<TaskResponse> findAll(UUID organizationId) {
        return tasks.findAllByProjectSpaceOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public TaskResponse findById(UUID id, UUID organizationId) { return response(requireTask(id, organizationId)); }

    @Transactional
    public TaskResponse create(UUID organizationId, TaskRequest request) {
        Project project = requireProject(request.projectId(), organizationId);
        validateDates(request.startDate(), request.endDate());
        validateWithinProject(request.startDate(), request.endDate(), project);
        Task task = new Task(project, request.name().trim());
        task.setStatus(request.status()); task.setStartDate(request.startDate()); task.setEndDate(request.endDate());
        return response(tasks.save(task));
    }

    @Transactional
    public TaskResponse update(UUID id, UUID organizationId, TaskRequest request) {
        Task task = requireTask(id, organizationId);
        validateDates(request.startDate(), request.endDate());
        Project targetProject = requireProject(request.projectId(), organizationId);
        validateWithinProject(request.startDate(), request.endDate(), targetProject);
        taskResourceService.validateSchedule(task, targetProject, request.status(), request.startDate(), request.endDate());
        if (!task.getProject().getId().equals(targetProject.getId())) taskResourceService.moveAssignmentsToProjectPool(task, targetProject);
        task.setProject(targetProject);
        task.setName(request.name().trim()); task.setStatus(request.status());
        task.setStartDate(request.startDate()); task.setEndDate(request.endDate());
        return response(tasks.save(task));
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) {
        Task task = requireTask(id, organizationId);
        java.time.LocalDate effectiveEnd = task.getEndDate() != null ? task.getEndDate() : task.getProject().getEndDate();
        if ((task.getStatus() == TaskStatus.DONE || effectiveEnd != null && effectiveEnd.isBefore(java.time.LocalDate.now()))
                && taskResourceAllocations.findAllByTaskId(task.getId()).stream()
                .anyMatch(allocation -> allocation.getProjectResourceAllocation().getResource() instanceof BudgetResource))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A completed task with committed budget cannot be deleted");
        taskResourceAllocations.deleteAllByTaskId(task.getId());
        tasks.delete(task);
    }

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

    private void validateWithinProject(java.time.LocalDate start, java.time.LocalDate end, Project project) {
        if (start != null && project.getStartDate() != null && start.isBefore(project.getStartDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task start date cannot be before the project starts");
        if (end != null && project.getEndDate() != null && end.isAfter(project.getEndDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task end date cannot be after the project ends");
    }

    private TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getName(), task.getStatus(), task.getStartDate(), task.getEndDate(), task.getCreatedAt());
    }
}
