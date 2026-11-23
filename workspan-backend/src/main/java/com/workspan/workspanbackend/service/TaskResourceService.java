package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.TaskResourceAllocationRequest;
import com.workspan.workspanbackend.dto.TaskResourceAllocationResponse;
import com.workspan.workspanbackend.dto.TaskResourceOptionResponse;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskResourceService {
    private final TaskRepository tasks;
    private final ProjectResourceAllocationRepository projectAllocations;
    private final TaskResourceAllocationRepository taskAllocations;

    public TaskResourceService(TaskRepository tasks, ProjectResourceAllocationRepository projectAllocations,
                               TaskResourceAllocationRepository taskAllocations) {
        this.tasks = tasks; this.projectAllocations = projectAllocations; this.taskAllocations = taskAllocations;
    }

    public List<TaskResourceAllocationResponse> findAssigned(UUID taskId, UUID organizationId) {
        Task task = requireTask(taskId, organizationId);
        return taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(taskId)
                .stream().map(allocation -> response(allocation, task)).toList();
    }

    public List<TaskResourceOptionResponse> findPool(UUID taskId, UUID organizationId) {
        Task task = requireTask(taskId, organizationId); Window requested = window(task);
        return projectAllocations.findAllByProjectIdOrderByResourceTypeAscResourceNameAsc(task.getProject().getId()).stream().map(projectAllocation -> {
            Resource resource = projectAllocation.getResource();
            List<TaskResourceAllocation> overlapping = taskAllocations.findAllByProjectResourceAllocationId(projectAllocation.getId()).stream()
                    .filter(item -> !item.getTask().getId().equals(taskId) && item.getTask().getStatus() != TaskStatus.DONE && overlaps(requested, window(item.getTask()))).toList();
            boolean alreadyAssigned = taskAllocations.findByTaskIdAndProjectResourceAllocationId(taskId, projectAllocation.getId()).isPresent();
            Integer quantity = null;
            if (resource instanceof HumanResource) quantity = Math.max(0, 100 - humanPeakLoad(resource, requested, taskId));
            else if (resource instanceof EquipmentResource)
                quantity = Math.max(0, (projectAllocation.getAllocatedQuantity() == null ? 0 : projectAllocation.getAllocatedQuantity())
                        - overlapping.stream().mapToInt(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum());
            BigDecimal amount = resource instanceof BudgetResource ? (projectAllocation.getAllocatedAmount() == null ? BigDecimal.ZERO : projectAllocation.getAllocatedAmount())
                    .subtract(taskAllocations.findAllByProjectResourceAllocationId(projectAllocation.getId()).stream()
                            .filter(item -> !item.getTask().getId().equals(taskId))
                            .map(item -> item.getAllocatedAmount() == null ? BigDecimal.ZERO : item.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal.ZERO) : null;
            String currency = resource instanceof BudgetResource budget ? budget.getCurrency() : null;
            return new TaskResourceOptionResponse(resource.getId(), resource.getName(), resource.getType(), quantity, amount, currency, alreadyAssigned);
        }).toList();
    }

    /**
     * Schedule changes no longer block on employee/equipment concurrency: overlapping work is allowed and reported as
     * overload by the workload analysis. The only hard rule left is that a task's resources belong to its project pool.
     */
    public void validateSchedule(Task task, Project targetProject, TaskStatus targetStatus, LocalDate startDate, LocalDate endDate) {
        for (TaskResourceAllocation current : taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(task.getId())) {
            projectAllocations.findByProjectIdAndResourceId(targetProject.getId(), current.getProjectResourceAllocation().getResource().getId())
                    .orElseThrow(() -> new ConflictException(ErrorCode.TASK_RESOURCE_NOT_IN_TARGET_POOL));
        }
    }

    @Transactional
    public void moveAssignmentsToProjectPool(Task task, Project targetProject) {
        List<TaskResourceAllocation> current = taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(task.getId());
        current.forEach(item -> item.setProjectResourceAllocation(projectAllocations.findByProjectIdAndResourceId(targetProject.getId(), item.getProjectResourceAllocation().getResource().getId())
                .orElseThrow(() -> new ConflictException(ErrorCode.TASK_RESOURCE_NOT_IN_TARGET_POOL))));
        taskAllocations.saveAll(current);
    }

    /** nextStart / nextEnd are the project's new planned dates; tasks' planned dates must stay inside them. */
    public void validateProjectSchedule(Project project, LocalDate nextStart, LocalDate nextEnd) {
        for (Task task : tasks.findAllByProjectIdOrderByNameAsc(project.getId())) {
            if (task.getBaselineStart() != null && nextStart != null && task.getBaselineStart().isBefore(nextStart))
                throw new BadRequestException(ErrorCode.PROJECT_STARTS_AFTER_TASK, Map.of("task", task.getName()));
            if (task.getBaselineEnd() != null && nextEnd != null && task.getBaselineEnd().isAfter(nextEnd))
                throw new BadRequestException(ErrorCode.PROJECT_ENDS_BEFORE_TASK, Map.of("task", task.getName()));
        }
    }

    @Transactional
    public TaskResourceAllocationResponse assign(UUID taskId, UUID organizationId, TaskResourceAllocationRequest request) {
        Task task = requireTask(taskId, organizationId);
        if (task.getStatus() == TaskStatus.DONE) throw new ConflictException(ErrorCode.TASK_COMPLETED_RESOURCE_LOCKED);
        ProjectResourceAllocation projectAllocation = projectAllocations.findByProjectIdAndResourceId(task.getProject().getId(), request.resourceId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.RESOURCE_NOT_IN_PROJECT_POOL));
        Resource resource = projectAllocation.getResource();
        if (!"AVAILABLE".equalsIgnoreCase(resource.getStatus())) throw new ConflictException(ErrorCode.RESOURCE_UNAVAILABLE);
        if (taskAllocations.findByTaskIdAndProjectResourceAllocationId(taskId, projectAllocation.getId()).isPresent())
            throw new ConflictException(ErrorCode.RESOURCE_ALREADY_ON_TASK);

        Window requestedWindow = window(task);
        if (requestedWindow.end() != null && requestedWindow.end().isBefore(LocalDate.now()))
            throw new ConflictException(ErrorCode.TASK_PERIOD_ENDED);
        List<TaskResourceAllocation> existing = taskAllocations.findAllByProjectResourceAllocationId(projectAllocation.getId());
        Integer allocationPercent = null;
        if (resource instanceof HumanResource) {
            // Overlapping assignments are allowed; the workload analysis reports days above 100% as overload.
            if (request.quantity() != null || request.amount() != null) throw new BadRequestException(ErrorCode.EMPLOYEE_TASK_ALLOCATION_INVALID);
            allocationPercent = request.allocationPercent() == null ? 100 : request.allocationPercent();
        } else if (resource instanceof EquipmentResource) {
            // A single assignment cannot exceed the units allocated to the project; concurrent use is reported as overload.
            if (request.quantity() == null || request.amount() != null || request.allocationPercent() != null) throw new BadRequestException(ErrorCode.EQUIPMENT_QUANTITY_REQUIRED);
            if (request.quantity() > (projectAllocation.getAllocatedQuantity() == null ? 0 : projectAllocation.getAllocatedQuantity()))
                throw new ConflictException(ErrorCode.PROJECT_UNITS_EXCEEDED);
        } else if (resource instanceof BudgetResource) {
            if (request.allocationPercent() != null) throw new BadRequestException(ErrorCode.BUDGET_AMOUNT_REQUIRED);
            if (request.amount() == null || request.quantity() != null) throw new BadRequestException(ErrorCode.BUDGET_AMOUNT_REQUIRED);
            if (request.amount().signum() <= 0) throw new BadRequestException(ErrorCode.BUDGET_AMOUNT_NOT_POSITIVE);
            BigDecimal concurrent = existing.stream()
                    .map(item -> item.getAllocatedAmount() == null ? BigDecimal.ZERO : item.getAllocatedAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (concurrent.add(request.amount()).compareTo(projectAllocation.getAllocatedAmount()) > 0) throw new ConflictException(ErrorCode.PROJECT_BUDGET_EXCEEDED);
        }

        TaskResourceAllocation allocation = new TaskResourceAllocation();
        allocation.setTask(task); allocation.setProjectResourceAllocation(projectAllocation);
        allocation.setAllocatedQuantity(request.quantity()); allocation.setAllocatedAmount(request.amount());
        allocation.setAllocationPercent(allocationPercent);
        return response(taskAllocations.saveAndFlush(allocation), task);
    }

    @Transactional
    public void remove(UUID taskId, UUID organizationId, UUID resourceId) {
        Task task = requireTask(taskId, organizationId);
        ProjectResourceAllocation projectAllocation = projectAllocations.findByProjectIdAndResourceId(task.getProject().getId(), resourceId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_RESOURCE_NOT_FOUND));
        TaskResourceAllocation assignment = taskAllocations.findByTaskIdAndProjectResourceAllocationId(taskId, projectAllocation.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_RESOURCE_NOT_FOUND));
        if (assignment.getProjectResourceAllocation().getResource() instanceof BudgetResource &&
                (task.getStatus() == TaskStatus.DONE || window(task).end() != null && window(task).end().isBefore(LocalDate.now())))
            throw new ConflictException(ErrorCode.TASK_ENDED_BUDGET_LOCKED);
        taskAllocations.delete(assignment);
    }

    private Task requireTask(UUID id, UUID organizationId) {
        return tasks.findByIdAndProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_NOT_FOUND));
    }
    private Window window(Task task) {
        return window(task, task.getProject(), ScheduleDates.taskStart(task), ScheduleDates.taskEnd(task));
    }
    /** taskStart / taskEnd are occupancy dates (actual when known, otherwise planned). */
    private Window window(Task task, Project project, LocalDate taskStart, LocalDate taskEnd) {
        return window(task, ScheduleDates.projectStart(project), ScheduleDates.projectEnd(project), taskStart, taskEnd);
    }
    private Window window(Task task, LocalDate projectStart, LocalDate projectEnd) {
        return window(task, projectStart, projectEnd, ScheduleDates.taskStart(task), ScheduleDates.taskEnd(task));
    }
    /** Falls back to the project window only when the task has no dates; real (late) task dates are never clamped. */
    private Window window(Task task, LocalDate projectStart, LocalDate projectEnd, LocalDate taskStart, LocalDate taskEnd) {
        LocalDate start = taskStart != null ? taskStart : projectStart;
        LocalDate end = taskEnd != null ? taskEnd : projectEnd;
        if (start != null && end != null && end.isBefore(start)) end = start;
        return new Window(start, end);
    }
    private boolean overlaps(Window left, Window right) {
        return (left.end() == null || right.start() == null || !left.end().isBefore(right.start()))
                && (right.end() == null || left.start() == null || !right.end().isBefore(left.start()));
    }
    private TaskResourceAllocationResponse response(TaskResourceAllocation allocation, Task task) {
        ProjectResourceAllocation projectAllocation = allocation.getProjectResourceAllocation();
        Resource resource = projectAllocation.getResource();
        String currency = resource instanceof BudgetResource budget ? budget.getCurrency() : null;
        Window window = window(task); LocalDate today = LocalDate.now();
        boolean active = task.getStatus() != TaskStatus.DONE && (window.start() == null || !today.isBefore(window.start()))
                && (window.end() == null || !today.isAfter(window.end()));
        LocalDate availableFrom = task.getStatus() == TaskStatus.DONE ? today
                : window.end() == null ? null : window.end().plusDays(1);
        return new TaskResourceAllocationResponse(resource.getId(), resource.getName(), resource.getType(),
                allocation.getAllocatedQuantity(), allocation.getAllocatedAmount(), currency, allocation.getAllocatedAt(),
                window.start(), window.end(), availableFrom, active,
                resource instanceof HumanResource ? allocation.effectivePercent() : null);
    }
    /** Highest daily charge (%) of an employee over a window, across all their unfinished task assignments. */
    private int humanPeakLoad(Resource resource, Window requested, UUID excludedTaskId) {
        List<TaskResourceAllocation> assignments = taskAllocations.findAllByProjectResourceAllocationResourceId(resource.getId()).stream()
                .filter(item -> !item.getTask().getId().equals(excludedTaskId) && item.getTask().getStatus() != TaskStatus.DONE).toList();
        if (assignments.isEmpty() || requested.start() == null || requested.end() == null) {
            return assignments.stream().filter(item -> overlaps(requested, window(item.getTask()))).mapToInt(TaskResourceAllocation::effectivePercent).sum();
        }
        int peak = 0;
        for (LocalDate day = requested.start(); !day.isAfter(requested.end()); day = day.plusDays(1)) {
            Window single = new Window(day, day);
            int load = assignments.stream().filter(item -> overlaps(single, window(item.getTask()))).mapToInt(TaskResourceAllocation::effectivePercent).sum();
            peak = Math.max(peak, load);
        }
        return peak;
    }

    private record Window(LocalDate start, LocalDate end) {}
}

