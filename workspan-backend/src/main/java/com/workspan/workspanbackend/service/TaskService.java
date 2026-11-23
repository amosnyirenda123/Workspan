package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.TaskRequest;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.dto.TaskScheduleRequest;
import com.workspan.workspanbackend.entity.BudgetResource;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskDependencyRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskCommentRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Task lifecycle. Date model:
 * <ul>
 *     <li>baselineStart / baselineEnd: planned dates, edited by the user, must sit inside the project's planned dates.</li>
 *     <li>startDate / endDate: actual dates. Filled automatically on status transitions (STARTED sets the actual
 *     start, DONE sets the actual end) and may be corrected manually; they can never be in the future.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final TaskResourceAllocationRepository taskResourceAllocations;
    private final TaskResourceService taskResourceService;
    private final TaskDependencyRepository dependencies;
    private final ProjectScheduleSync projectScheduleSync;
    private final TaskCommentRepository comments;

    public TaskService(TaskRepository tasks, ProjectRepository projects, TaskResourceAllocationRepository taskResourceAllocations,
                       TaskResourceService taskResourceService, TaskDependencyRepository dependencies, ProjectScheduleSync projectScheduleSync,
                       TaskCommentRepository comments) {
        this.tasks = tasks; this.projects = projects; this.taskResourceAllocations = taskResourceAllocations;
        this.taskResourceService = taskResourceService; this.dependencies = dependencies; this.projectScheduleSync = projectScheduleSync;
        this.comments = comments;
    }

    public List<TaskResponse> findAll(UUID organizationId) {
        return tasks.findAllByProjectSpaceOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public TaskResponse findById(UUID id, UUID organizationId) { return response(requireTask(id, organizationId)); }

    @Transactional
    public TaskResponse create(UUID organizationId, TaskRequest request) {
        Project project = requireProject(request.projectId(), organizationId);
        Schedule schedule = resolve(request.status(), request.baselineStart(), request.baselineEnd(), request.startDate(), request.endDate(), request.progress(), 0);
        validateWithinProject(schedule.plannedStart(), schedule.plannedEnd(), project);
        Task task = new Task(project, request.name().trim());
        task.setStatus(request.status());
        task.setPriority(request.priority() == null ? com.workspan.workspanbackend.entity.TaskPriority.NORMAL : request.priority());
        task.setEstimatePoints(request.estimatePoints()); task.setDueDate(request.dueDate());
        schedule.applyTo(task);
        Task saved = tasks.save(task);
        projectScheduleSync.refreshActualDates(project);
        return response(saved);
    }

    @Transactional
    public TaskResponse update(UUID id, UUID organizationId, TaskRequest request) {
        Task task = requireTask(id, organizationId);
        Project previousProject = task.getProject();
        Project targetProject = requireProject(request.projectId(), organizationId);
        boolean moving = !previousProject.getId().equals(targetProject.getId());
        if (moving && dependencies.existsByPredecessorTaskIdOrSuccessorTaskId(task.getId(), task.getId()))
            throw new ConflictException(ErrorCode.TASK_HAS_DEPENDENCIES);
        Schedule schedule = resolve(request.status(), request.baselineStart(), request.baselineEnd(), request.startDate(), request.endDate(), request.progress(), task.getProgress());
        validateWithinProject(schedule.plannedStart(), schedule.plannedEnd(), targetProject);
        taskResourceService.validateSchedule(task, targetProject, request.status(), schedule.occupancyStart(), schedule.occupancyEnd());
        if (moving) taskResourceService.moveAssignmentsToProjectPool(task, targetProject);
        if (moving) task.setSprint(null);
        task.setProject(targetProject);
        task.setName(request.name().trim());
        task.setStatus(request.status());
        if (request.priority() != null) task.setPriority(request.priority());
        task.setEstimatePoints(request.estimatePoints()); task.setDueDate(request.dueDate());
        schedule.applyTo(task);
        Task saved = tasks.save(task);
        projectScheduleSync.refreshActualDates(targetProject);
        if (moving) projectScheduleSync.refreshActualDates(previousProject);
        return response(saved);
    }

    /** Schedule-only update (planned dates, actual dates, progress) used by the Gantt view. */
    @Transactional
    public TaskResponse updateSchedule(UUID id, UUID organizationId, TaskScheduleRequest request) {
        Task task = requireTask(id, organizationId);
        applySchedule(task, request.baselineStart(), request.baselineEnd(), request.startDate(), request.endDate(),
                request.progress() == null ? task.getProgress() : request.progress());
        return response(task);
    }

    /** Validates and persists a new schedule for a task, keeping its status. Used by the Gantt view and rescheduling. */
    @Transactional
    public void applySchedule(Task task, LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd, Integer progress) {
        Project project = task.getProject();
        Schedule schedule = resolve(task.getStatus(), plannedStart, plannedEnd, actualStart, actualEnd, progress, task.getProgress());
        validateWithinProject(schedule.plannedStart(), schedule.plannedEnd(), project);
        taskResourceService.validateSchedule(task, project, task.getStatus(), schedule.occupancyStart(), schedule.occupancyEnd());
        schedule.applyTo(task);
        tasks.save(task);
        projectScheduleSync.refreshActualDates(project);
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) {
        Task task = requireTask(id, organizationId);
        Project project = task.getProject();
        LocalDate occupancyEnd = ScheduleDates.taskEnd(task);
        LocalDate effectiveEnd = occupancyEnd != null ? occupancyEnd : ScheduleDates.projectEnd(project);
        if ((task.getStatus() == TaskStatus.DONE || effectiveEnd != null && effectiveEnd.isBefore(LocalDate.now()))
                && taskResourceAllocations.findAllByTaskId(task.getId()).stream()
                .anyMatch(allocation -> allocation.getProjectResourceAllocation().getResource() instanceof BudgetResource))
            throw new ConflictException(ErrorCode.TASK_BUDGET_COMMITTED);
        dependencies.deleteAllInvolvingTask(task.getId());
        comments.deleteAllByTaskId(task.getId());
        taskResourceAllocations.deleteAllByTaskId(task.getId());
        tasks.delete(task);
        tasks.flush();
        projectScheduleSync.refreshActualDates(project);
    }

    Task requireTask(UUID id, UUID organizationId) {
        return tasks.findByIdAndProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_NOT_FOUND));
    }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }

    /**
     * Normalises dates according to the status and validates them.
     * TODO: no actual dates, progress 0. STARTED: actual start defaults to today, no actual end.
     * BLOCKED: no actual end. DONE: actual end defaults to today, actual start defaults to the planned start
     * (or the actual end when the plan is in the future), progress 100.
     */
    private Schedule resolve(TaskStatus status, LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd,
                             Integer requestedProgress, int currentProgress) {
        LocalDate today = LocalDate.now();
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart))
            throw new BadRequestException(ErrorCode.PLANNED_DATES_INVALID);
        int progress = requestedProgress != null ? requestedProgress : currentProgress;
        switch (status) {
            case TODO -> { actualStart = null; actualEnd = null; progress = 0; }
            case STARTED -> { actualEnd = null; if (actualStart == null) actualStart = today; if (progress >= 100) progress = 99; }
            case BLOCKED -> { actualEnd = null; if (progress >= 100) progress = 99; }
            case DONE -> {
                if (actualEnd == null) actualEnd = today;
                if (actualStart == null) actualStart = plannedStart != null && !plannedStart.isAfter(actualEnd) ? plannedStart : actualEnd;
                progress = 100;
            }
        }
        if (actualStart != null && actualStart.isAfter(today)) throw new BadRequestException(ErrorCode.ACTUAL_START_IN_FUTURE);
        if (actualEnd != null && actualEnd.isAfter(today)) throw new BadRequestException(ErrorCode.ACTUAL_END_IN_FUTURE);
        if (actualStart != null && actualEnd != null && actualEnd.isBefore(actualStart))
            throw new BadRequestException(ErrorCode.ACTUAL_DATES_INVALID);
        if (progress < 0 || progress > 100) throw new BadRequestException(ErrorCode.PROGRESS_OUT_OF_RANGE);
        return new Schedule(plannedStart, plannedEnd, actualStart, actualEnd, progress);
    }

    private void validateWithinProject(LocalDate start, LocalDate end, Project project) {
        if (start != null && project.getBaselineStart() != null && start.isBefore(project.getBaselineStart()))
            throw new BadRequestException(ErrorCode.TASK_STARTS_BEFORE_PROJECT);
        if (end != null && project.getBaselineEnd() != null && end.isAfter(project.getBaselineEnd()))
            throw new BadRequestException(ErrorCode.TASK_ENDS_AFTER_PROJECT);
        if (start != null && project.getBaselineEnd() != null && start.isAfter(project.getBaselineEnd()))
            throw new BadRequestException(ErrorCode.TASK_STARTS_AFTER_PROJECT_END);
    }


    TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getSprint() == null ? null : task.getSprint().getId(), task.getName(), task.getStatus(),
                task.getPriority(), task.getEstimatePoints(), task.getDueDate(),
                task.getBaselineStart(), task.getBaselineEnd(), task.getStartDate(), task.getEndDate(), task.getProgress(), task.getCreatedAt());
    }

    private record Schedule(LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd, int progress) {
        LocalDate occupancyStart() { return ScheduleDates.taskStart(actualStart, plannedStart); }
        LocalDate occupancyEnd() { return ScheduleDates.taskEnd(actualStart, actualEnd, plannedEnd); }
        void applyTo(Task task) {
            task.setBaselineStart(plannedStart); task.setBaselineEnd(plannedEnd);
            task.setStartDate(actualStart); task.setEndDate(actualEnd);
            task.setProgress(progress);
        }
    }
}
