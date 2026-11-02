package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.ProjectResourceAllocationRequest;
import com.workspan.workspanbackend.dto.ProjectResourceAllocationResponse;
import com.workspan.workspanbackend.dto.ResourceResponse;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.ResourceRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
public class ProjectResourceService {
    private final ProjectRepository projects;
    private final ResourceRepository resources;
    private final ProjectResourceAllocationRepository allocations;
    private final TaskResourceAllocationRepository taskAllocations;

    public ProjectResourceService(ProjectRepository projects, ResourceRepository resources,
                                  ProjectResourceAllocationRepository allocations, TaskResourceAllocationRepository taskAllocations) {
        this.projects = projects; this.resources = resources; this.allocations = allocations; this.taskAllocations = taskAllocations;
    }

    public List<ResourceResponse> findPool(UUID projectId, UUID organizationId) {
        Project project = requireProject(projectId, organizationId);
        return resources.findAllBySpaceIdOrderByTypeAscNameAsc(project.getSpace().getId()).stream().map(this::resourceResponse).toList();
    }

    public List<ProjectResourceAllocationResponse> findAssigned(UUID projectId, UUID organizationId) {
        requireProject(projectId, organizationId);
        return allocations.findAllByProjectIdOrderByResourceTypeAscResourceNameAsc(projectId).stream().map(this::allocationResponse).toList();
    }

    @Transactional
    public ProjectResourceAllocationResponse assign(UUID projectId, UUID organizationId, ProjectResourceAllocationRequest request) {
        Project project = requireProject(projectId, organizationId);
        if (project.getEndDate() != null && project.getEndDate().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resources cannot be assigned to a project that has ended");
        Resource resource = resources.findForAllocation(request.resourceId(), organizationId)
                .orElseThrow(() -> notFound("Resource not found"));
        if (!resource.getSpace().getId().equals(project.getSpace().getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A project can only use resources from its own space");
        if (!"AVAILABLE".equalsIgnoreCase(resource.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This resource is not available");
        if (allocations.findByProjectIdAndResourceId(projectId, resource.getId()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This resource is already in the project pool");

        ProjectResourceAllocation allocation = new ProjectResourceAllocation();
        allocation.setProject(project); allocation.setResource(resource);
        if (resource instanceof HumanResource) {
            if (request.quantity() != null || request.amount() != null) throw badRequest("An employee is assigned as one person");
            if (allocations.findAllByResourceId(resource.getId()).stream().anyMatch(existing -> overlaps(window(project), window(existing.getProject()))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This employee is already allocated to an overlapping project");
        } else if (resource instanceof EquipmentResource equipment) {
            if (request.quantity() == null || request.amount() != null) throw badRequest("Choose the number of equipment units to allocate");
            long alreadyAllocated = allocations.findAllByResourceId(resource.getId()).stream()
                    .filter(existing -> overlaps(window(project), window(existing.getProject())))
                    .mapToLong(existing -> existing.getAllocatedQuantity() == null ? 0 : existing.getAllocatedQuantity()).sum();
            if (alreadyAllocated + request.quantity() > equipment.getQuantity())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This allocation exceeds the available equipment quantity");
            allocation.setAllocatedQuantity(request.quantity());
        } else if (resource instanceof BudgetResource budget) {
            if (request.amount() == null || request.quantity() != null) throw badRequest("Choose a budget amount to allocate");
            BigDecimal available = budget.getRemaining().subtract(allocations.sumAllocatedAmount(resource.getId())).max(BigDecimal.ZERO);
            if (request.amount().signum() <= 0 || request.amount().compareTo(available) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This allocation exceeds the remaining budget");
            allocation.setAllocatedAmount(request.amount());
        }
        try {
            return allocationResponse(allocations.saveAndFlush(allocation));
        } catch (DataIntegrityViolationException cause) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This resource is already in the project pool");
        }
    }

    @Transactional
    public void remove(UUID projectId, UUID organizationId, UUID resourceId) {
        Project project = requireProject(projectId, organizationId);
        ProjectResourceAllocation allocation = allocations.findByProjectIdAndResourceId(projectId, resourceId)
                .orElseThrow(() -> notFound("Project resource assignment not found"));
        if (taskAllocations.existsByProjectResourceAllocationId(allocation.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove this resource from its tasks before removing it from the project");
        if (allocation.getResource() instanceof BudgetResource budget) {
            if (project.getEndDate() != null && project.getEndDate().isBefore(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Budget cannot be withdrawn after the project has ended");
        }
        allocations.delete(allocation);
    }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("Project not found"));
    }
    private ResourceResponse resourceResponse(Resource resource) {
        Integer quantity = resource instanceof EquipmentResource equipment ? equipment.getQuantity() : null;
        Integer availableQuantity = null;
        if (resource instanceof EquipmentResource equipment)
            availableQuantity = Math.max(0, equipment.getQuantity() - allocations.sumAllocatedQuantity(resource.getId()).intValue());
        else if (resource instanceof HumanResource)
            availableQuantity = allocations.findAllByResourceId(resource.getId()).stream().anyMatch(this::isProjectAllocationActive) ? 0 : 1;
        BigDecimal amount = resource instanceof BudgetResource budget ? budget.getAmount() : null;
        BigDecimal consumed = resource instanceof BudgetResource budget ? budget.getConsumed() : null;
        BigDecimal remaining = resource instanceof BudgetResource budget ? budget.getRemaining() : null;
        BigDecimal availableAmount = resource instanceof BudgetResource budget ? budget.getRemaining().subtract(allocations.sumAllocatedAmount(resource.getId())).max(BigDecimal.ZERO) : null;
        String currency = resource instanceof BudgetResource budget ? budget.getCurrency() : null;
        String email = resource instanceof HumanResource human ? human.getEmail() : null;
        String jobTitle = resource instanceof HumanResource human ? human.getJobTitle() : null;
        return new ResourceResponse(resource.getId(), resource.getSpace().getId(), resource.getName(), resource.getType(),
                resource.getStatus(), quantity, availableQuantity, amount, consumed, remaining, availableAmount, currency, email, jobTitle);
    }
    private ProjectResourceAllocationResponse allocationResponse(ProjectResourceAllocation allocation) {
        Resource resource = allocation.getResource();
        String currency = resource instanceof BudgetResource budget ? budget.getCurrency() : null;
        List<TaskResourceAllocation> activeAssignments = taskAllocations.findAllByProjectResourceAllocationId(allocation.getId()).stream()
                .filter(this::isTaskAllocationActive).toList();
        List<TaskResourceAllocation> scheduledAssignments = taskAllocations.findAllByProjectResourceAllocationId(allocation.getId()).stream()
                .filter(this::isTaskAllocationScheduled).toList();
        int activeTaskCount = activeAssignments.size();
        int scheduledTaskCount = scheduledAssignments.size();
        Integer taskQuantity = null;
        if (resource instanceof HumanResource) taskQuantity = activeTaskCount;
        else if (resource instanceof EquipmentResource)
            taskQuantity = activeAssignments.stream().mapToInt(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum();
        Integer scheduledTaskQuantity = null;
        if (resource instanceof HumanResource) scheduledTaskQuantity = scheduledTaskCount;
        else if (resource instanceof EquipmentResource)
            scheduledTaskQuantity = scheduledAssignments.stream().mapToInt(item -> item.getAllocatedQuantity() == null ? 0 : item.getAllocatedQuantity()).sum();
        List<TaskResourceAllocation> committedBudgetAssignments = resource instanceof BudgetResource
                ? taskAllocations.findAllByProjectResourceAllocationId(allocation.getId()) : List.of();
        BigDecimal taskAmount = resource instanceof BudgetResource ? committedBudgetAssignments.stream().map(item -> item.getAllocatedAmount() == null ? BigDecimal.ZERO : item.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal scheduledTaskAmount = resource instanceof BudgetResource ? committedBudgetAssignments.stream().filter(this::isTaskAllocationScheduled).map(item -> item.getAllocatedAmount() == null ? BigDecimal.ZERO : item.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        Integer freeQuantity = null;
        if (resource instanceof HumanResource) freeQuantity = Math.max(0, 1 - activeTaskCount);
        else if (resource instanceof EquipmentResource)
            freeQuantity = Math.max(0, (allocation.getAllocatedQuantity() == null ? 0 : allocation.getAllocatedQuantity()) - (taskQuantity == null ? 0 : taskQuantity));
        BigDecimal freeAmount = resource instanceof BudgetResource ? (allocation.getAllocatedAmount() == null ? BigDecimal.ZERO : allocation.getAllocatedAmount()).subtract(taskAmount == null ? BigDecimal.ZERO : taskAmount).max(BigDecimal.ZERO) : null;
        Project project = allocation.getProject(); LocalDate today = LocalDate.now();
        boolean active = (project.getStartDate() == null || !today.isBefore(project.getStartDate()))
                && (project.getEndDate() == null || !today.isAfter(project.getEndDate()));
        LocalDate availableFrom = project.getEndDate() == null ? null : project.getEndDate().plusDays(1);
        return new ProjectResourceAllocationResponse(resource.getId(), resource.getName(), resource.getType(),
                allocation.getAllocatedQuantity(), allocation.getAllocatedAmount(), currency, allocation.getAllocatedAt(),
                activeTaskCount, scheduledTaskCount, taskQuantity, scheduledTaskQuantity, taskAmount, scheduledTaskAmount,
                freeQuantity, freeAmount, project.getStartDate(), project.getEndDate(), availableFrom, active);
    }
    private boolean isTaskAllocationActive(TaskResourceAllocation allocation) {
        Task task = allocation.getTask(); Project project = task.getProject(); LocalDate today = LocalDate.now();
        if (task.getStatus() == TaskStatus.DONE || project.getEndDate() != null && today.isAfter(project.getEndDate())) return false;
        LocalDate start = task.getStartDate() != null ? task.getStartDate() : project.getStartDate();
        LocalDate end = task.getEndDate() != null ? task.getEndDate() : project.getEndDate();
        if (project.getStartDate() != null && (start == null || start.isBefore(project.getStartDate()))) start = project.getStartDate();
        if (project.getEndDate() != null && (end == null || end.isAfter(project.getEndDate()))) end = project.getEndDate();
        return (start == null || !today.isBefore(start)) && (end == null || !today.isAfter(end));
    }
    private boolean isTaskAllocationScheduled(TaskResourceAllocation allocation) {
        if (allocation.getTask().getStatus() == TaskStatus.DONE) return false;
        Window window = taskWindow(allocation);
        return window.end() == null || !LocalDate.now().isAfter(window.end());
    }
    private Window taskWindow(TaskResourceAllocation allocation) {
        Task task = allocation.getTask(); Project project = task.getProject();
        LocalDate start = task.getStartDate() != null ? task.getStartDate() : project.getStartDate();
        LocalDate end = task.getEndDate() != null ? task.getEndDate() : project.getEndDate();
        if (project.getStartDate() != null && (start == null || start.isBefore(project.getStartDate()))) start = project.getStartDate();
        if (project.getEndDate() != null && (end == null || end.isAfter(project.getEndDate()))) end = project.getEndDate();
        return new Window(start, end);
    }
    private boolean isProjectAllocationActive(ProjectResourceAllocation allocation) {
        LocalDate today = LocalDate.now(); Project project = allocation.getProject();
        return (project.getStartDate() == null || !today.isBefore(project.getStartDate()))
                && (project.getEndDate() == null || !today.isAfter(project.getEndDate()));
    }
    private Window window(Project project) { return new Window(project.getStartDate(), project.getEndDate()); }
    private boolean overlaps(Window left, Window right) {
        return (left.end() == null || right.start() == null || !left.end().isBefore(right.start()))
                && (right.end() == null || left.start() == null || !right.end().isBefore(left.start()));
    }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record Window(LocalDate start, LocalDate end) {}
}
