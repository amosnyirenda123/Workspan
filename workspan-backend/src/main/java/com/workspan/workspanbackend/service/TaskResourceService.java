package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskResourceAllocationRequest;
import com.workspan.workspanbackend.dto.TaskResourceAllocationResponse;
import com.workspan.workspanbackend.dto.TaskResourceOptionResponse;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
            if (resource instanceof HumanResource) quantity = overlapping.isEmpty() ? 1 : 0;
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

    public void validateSchedule(Task task, Project targetProject, TaskStatus targetStatus, LocalDate startDate, LocalDate endDate) {
        List<TaskResourceAllocation> currentAssignments = taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(task.getId());
        if (currentAssignments.isEmpty()) return;
        Window requested = window(task, targetProject, startDate, endDate);
        for (TaskResourceAllocation current : currentAssignments) {
            ProjectResourceAllocation targetPoolAllocation = projectAllocations.findByProjectIdAndResourceId(targetProject.getId(), current.getProjectResourceAllocation().getResource().getId())
                    .orElseThrow(() -> conflict("The task's resource must be in the target project's pool"));
            Resource resource = targetPoolAllocation.getResource();
            if (targetStatus == TaskStatus.DONE) continue;
            List<TaskResourceAllocation> overlapping = taskAllocations.findAllByProjectResourceAllocationId(targetPoolAllocation.getId()).stream()
                    .filter(item -> !item.getTask().getId().equals(task.getId()) && item.getTask().getStatus() != TaskStatus.DONE && overlaps(requested, window(item.getTask()))).toList();
            if (resource instanceof HumanResource && !overlapping.isEmpty()) throw conflict("The task dates overlap another task using employee " + resource.getName());
            if (resource instanceof EquipmentResource) {
                long booked = overlapping.stream().mapToLong(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum();
                if (booked + (current.getAllocatedQuantity() == null ? 0 : current.getAllocatedQuantity()) > (targetPoolAllocation.getAllocatedQuantity() == null ? 0 : targetPoolAllocation.getAllocatedQuantity()))
                    throw conflict("The task dates exceed the project's available equipment for " + resource.getName());
            }
        }
    }

    @Transactional
    public void moveAssignmentsToProjectPool(Task task, Project targetProject) {
        List<TaskResourceAllocation> current = taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(task.getId());
        current.forEach(item -> item.setProjectResourceAllocation(projectAllocations.findByProjectIdAndResourceId(targetProject.getId(), item.getProjectResourceAllocation().getResource().getId())
                .orElseThrow(() -> conflict("The task's resource must be in the target project's pool"))));
        taskAllocations.saveAll(current);
    }

    public void validateProjectSchedule(Project project, LocalDate nextStart, LocalDate nextEnd) {
        for (Task task : tasks.findAllByProjectIdOrderByNameAsc(project.getId())) {
            if (task.getStartDate() != null && nextStart != null && task.getStartDate().isBefore(nextStart))
                throw badRequest("A project cannot start after the start date of task " + task.getName());
            if (task.getEndDate() != null && nextEnd != null && task.getEndDate().isAfter(nextEnd))
                throw badRequest("A project cannot end before the end date of task " + task.getName());
            if (task.getStatus() == TaskStatus.DONE) continue;
            Window requested = window(task, nextStart, nextEnd);
            for (TaskResourceAllocation current : taskAllocations.findAllByTaskIdOrderByAllocatedAtAsc(task.getId())) {
                ProjectResourceAllocation poolAllocation = current.getProjectResourceAllocation();
                List<TaskResourceAllocation> overlaps = taskAllocations.findAllByProjectResourceAllocationId(poolAllocation.getId()).stream()
                        .filter(other -> !other.getTask().getId().equals(task.getId()) && other.getTask().getStatus() != TaskStatus.DONE
                                && overlaps(requested, other.getTask().getProject().getId().equals(project.getId())
                                    ? window(other.getTask(), nextStart, nextEnd) : window(other.getTask()))).toList();
                Resource resource = poolAllocation.getResource();
                if (resource instanceof HumanResource && !overlaps.isEmpty()) throw conflict("Project dates overlap task assignments for employee " + resource.getName());
                if (resource instanceof EquipmentResource) {
                    long booked = overlaps.stream().mapToLong(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum();
                    if (booked + (current.getAllocatedQuantity() == null ? 0 : current.getAllocatedQuantity()) > (poolAllocation.getAllocatedQuantity() == null ? 0 : poolAllocation.getAllocatedQuantity()))
                        throw conflict("Project dates exceed task equipment availability for " + resource.getName());
                }
            }
        }
    }

    @Transactional
    public TaskResourceAllocationResponse assign(UUID taskId, UUID organizationId, TaskResourceAllocationRequest request) {
        Task task = requireTask(taskId, organizationId);
        if (task.getStatus() == TaskStatus.DONE) throw conflict("Resources cannot be assigned to a completed task");
        ProjectResourceAllocation projectAllocation = projectAllocations.findByProjectIdAndResourceId(task.getProject().getId(), request.resourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assign the resource to the project pool first"));
        Resource resource = projectAllocation.getResource();
        if (!"AVAILABLE".equalsIgnoreCase(resource.getStatus())) throw conflict("This resource is not available");
        if (taskAllocations.findByTaskIdAndProjectResourceAllocationId(taskId, projectAllocation.getId()).isPresent())
            throw conflict("This resource is already assigned to the task");

        Window requestedWindow = window(task);
        if (requestedWindow.end() != null && requestedWindow.end().isBefore(LocalDate.now()))
            throw conflict("Resources cannot be assigned to a task whose scheduled period has ended");
        List<TaskResourceAllocation> existing = taskAllocations.findAllByProjectResourceAllocationId(projectAllocation.getId());
        if (resource instanceof HumanResource) {
            if (request.quantity() != null || request.amount() != null) throw badRequest("An employee is assigned as one person");
            if (existing.stream().anyMatch(item -> overlaps(requestedWindow, window(item.getTask())) && item.getTask().getStatus() != TaskStatus.DONE))
                throw conflict("This employee is already assigned to another task during this period");
        } else if (resource instanceof EquipmentResource) {
            if (request.quantity() == null || request.amount() != null) throw badRequest("Choose the number of equipment units to allocate");
            long concurrent = existing.stream().filter(item -> overlaps(requestedWindow, window(item.getTask())) && item.getTask().getStatus() != TaskStatus.DONE)
                    .mapToLong(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum();
            if (concurrent + request.quantity() > projectAllocation.getAllocatedQuantity()) throw conflict("This allocation exceeds the project's available equipment");
        } else if (resource instanceof BudgetResource) {
            if (request.amount() == null || request.quantity() != null) throw badRequest("Choose a budget amount to allocate");
            if (request.amount().signum() <= 0) throw badRequest("The budget amount must be greater than zero");
            BigDecimal concurrent = existing.stream()
                    .map(item -> item.getAllocatedAmount() == null ? BigDecimal.ZERO : item.getAllocatedAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (concurrent.add(request.amount()).compareTo(projectAllocation.getAllocatedAmount()) > 0) throw conflict("This allocation exceeds the project's available budget");
        }

        TaskResourceAllocation allocation = new TaskResourceAllocation();
        allocation.setTask(task); allocation.setProjectResourceAllocation(projectAllocation);
        allocation.setAllocatedQuantity(request.quantity()); allocation.setAllocatedAmount(request.amount());
        return response(taskAllocations.saveAndFlush(allocation), task);
    }

    @Transactional
    public void remove(UUID taskId, UUID organizationId, UUID resourceId) {
        Task task = requireTask(taskId, organizationId);
        ProjectResourceAllocation projectAllocation = projectAllocations.findByProjectIdAndResourceId(task.getProject().getId(), resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project resource allocation not found"));
        TaskResourceAllocation assignment = taskAllocations.findByTaskIdAndProjectResourceAllocationId(taskId, projectAllocation.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task resource assignment not found"));
        if (assignment.getProjectResourceAllocation().getResource() instanceof BudgetResource &&
                (task.getStatus() == TaskStatus.DONE || window(task).end() != null && window(task).end().isBefore(LocalDate.now())))
            throw conflict("Budget cannot be withdrawn after the task has ended");
        taskAllocations.delete(assignment);
    }

    private Task requireTask(UUID id, UUID organizationId) {
        return tasks.findByIdAndProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }
    private Window window(Task task) {
        Project project = task.getProject();
        return window(task, project, task.getStartDate(), task.getEndDate());
    }
    private Window window(Task task, Project project, LocalDate taskStart, LocalDate taskEnd) {
        return window(task, project.getStartDate(), project.getEndDate(), taskStart, taskEnd);
    }
    private Window window(Task task, LocalDate projectStart, LocalDate projectEnd) {
        return window(task, projectStart, projectEnd, task.getStartDate(), task.getEndDate());
    }
    private Window window(Task task, LocalDate projectStart, LocalDate projectEnd, LocalDate taskStart, LocalDate taskEnd) {
        LocalDate start = taskStart != null ? taskStart : projectStart;
        LocalDate end = taskEnd != null ? taskEnd : projectEnd;
        if (projectStart != null && (start == null || start.isBefore(projectStart))) start = projectStart;
        if (projectEnd != null && (end == null || end.isAfter(projectEnd))) end = projectEnd;
        if (start != null && end != null && end.isBefore(start)) throw badRequest("Task dates do not overlap its project dates");
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
                window.start(), window.end(), availableFrom, active);
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Window(LocalDate start, LocalDate end) {}
}

