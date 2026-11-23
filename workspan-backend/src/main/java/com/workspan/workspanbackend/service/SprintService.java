package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.SprintRequest;
import com.workspan.workspanbackend.dto.SprintResponse;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.SprintRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SprintService {
    private final SprintRepository sprints;
    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public SprintService(SprintRepository sprints, ProjectRepository projects, TaskRepository tasks) {
        this.sprints = sprints; this.projects = projects; this.tasks = tasks;
    }

    public List<SprintResponse> list(UUID projectId, UUID organizationId) {
        requireProject(projectId, organizationId);
        return sprints.findAllByProjectIdOrderByStartDateDescNameAsc(projectId).stream().map(this::response).toList();
    }

    @Transactional
    public SprintResponse create(UUID projectId, UUID organizationId, SprintRequest request) {
        Project project = requireProject(projectId, organizationId);
        if (!projectId.equals(request.projectId())) throw new BadRequestException(ErrorCode.SPRINT_TASK_PROJECT_MISMATCH);
        validate(request, null);
        Sprint sprint = new Sprint(); sprint.setProject(project); apply(sprint, request);
        return response(sprints.save(sprint));
    }

    @Transactional
    public SprintResponse update(UUID sprintId, UUID organizationId, SprintRequest request) {
        Sprint sprint = requireSprint(sprintId, organizationId);
        if (!sprint.getProject().getId().equals(request.projectId())) throw new BadRequestException(ErrorCode.SPRINT_TASK_PROJECT_MISMATCH);
        validate(request, sprintId); apply(sprint, request);
        return response(sprints.save(sprint));
    }

    @Transactional
    public void delete(UUID sprintId, UUID organizationId) {
        Sprint sprint = requireSprint(sprintId, organizationId);
        tasks.findAllBySprintId(sprintId).forEach(task -> task.setSprint(null));
        sprints.delete(sprint);
    }

    @Transactional
    public void addTask(UUID sprintId, UUID taskId, UUID organizationId) {
        Sprint sprint = requireSprint(sprintId, organizationId);
        Task task = tasks.findByIdAndProjectId(taskId, sprint.getProject().getId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.SPRINT_TASK_PROJECT_MISMATCH));
        task.setSprint(sprint); tasks.save(task);
    }

    @Transactional
    public void removeTask(UUID sprintId, UUID taskId, UUID organizationId) {
        Sprint sprint = requireSprint(sprintId, organizationId);
        Task task = tasks.findByIdAndProjectId(taskId, sprint.getProject().getId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.SPRINT_TASK_PROJECT_MISMATCH));
        if (task.getSprint() != null && task.getSprint().getId().equals(sprintId)) { task.setSprint(null); tasks.save(task); }
    }

    public UUID projectId(UUID sprintId, UUID organizationId) { return requireSprint(sprintId, organizationId).getProject().getId(); }

    private void validate(SprintRequest request, UUID currentId) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate()))
            throw new BadRequestException(ErrorCode.SPRINT_DATES_INVALID);
        boolean sameName = sprints.existsByProjectIdAndNameIgnoreCase(request.projectId(), request.name().trim());
        if (sameName && (currentId == null || !sprints.findById(currentId).map(s -> s.getName().equalsIgnoreCase(request.name().trim())).orElse(false)))
            throw new ConflictException(ErrorCode.SPRINT_NAME_TAKEN);
        if (request.status() == SprintStatus.ACTIVE && sprints.existsByProjectIdAndStatus(request.projectId(), SprintStatus.ACTIVE)
                && (currentId == null || sprints.findById(currentId).map(s -> s.getStatus() != SprintStatus.ACTIVE).orElse(true)))
            throw new ConflictException(ErrorCode.SPRINT_ACTIVE_EXISTS);
    }

    private void apply(Sprint sprint, SprintRequest request) {
        sprint.setName(request.name().trim());
        sprint.setGoal(request.goal() == null || request.goal().isBlank() ? null : request.goal().trim());
        sprint.setStartDate(request.startDate()); sprint.setEndDate(request.endDate()); sprint.setStatus(request.status());
    }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private Sprint requireSprint(UUID id, UUID organizationId) {
        return sprints.findById(id).filter(sprint -> sprint.getProject().getSpace().getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private SprintResponse response(Sprint sprint) {
        return new SprintResponse(sprint.getId(), sprint.getProject().getId(), sprint.getName(), sprint.getGoal(), sprint.getStartDate(), sprint.getEndDate(), sprint.getStatus(),
                Math.toIntExact(tasks.countBySprintId(sprint.getId())), Math.toIntExact(tasks.countBySprintIdAndStatus(sprint.getId(), TaskStatus.DONE)), sprint.getCreatedAt());
    }
}
