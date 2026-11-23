package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.ResourceRequest;
import com.workspan.workspanbackend.dto.ResourceResponse;
import com.workspan.workspanbackend.entity.*;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.ResourceRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResourceService {
    private final ResourceRepository resources;
    private final SpaceRepository spaces;
    private final HumanResourceRepository employees;
    private final ProjectResourceAllocationRepository allocations;

    public ResourceService(ResourceRepository resources, SpaceRepository spaces, HumanResourceRepository employees,
                           ProjectResourceAllocationRepository allocations) {
        this.resources = resources; this.spaces = spaces; this.employees = employees; this.allocations = allocations;
    }

    public List<ResourceResponse> findSpacePool(UUID spaceId, UUID organizationId) {
        requireSpace(spaceId, organizationId);
        return resources.findAllBySpaceIdOrderByTypeAscNameAsc(spaceId).stream().map(this::response).toList();
    }

    @Transactional
    public ResourceResponse create(UUID spaceId, UUID organizationId, ResourceRequest request) {
        Space space = requireSpace(spaceId, organizationId);
        Resource resource;
        if (request.type() == ResourceType.EQUIPMENT) {
            if (request.quantity() == null || request.amount() != null || request.currency() != null)
                throw new BadRequestException(ErrorCode.EQUIPMENT_FIELDS_INVALID);
            EquipmentResource equipment = new EquipmentResource();
            equipment.setQuantity(request.quantity());
            resource = equipment;
        } else if (request.type() == ResourceType.BUDGET) {
            if (request.amount() == null || request.quantity() != null)
                throw new BadRequestException(ErrorCode.BUDGET_FIELDS_INVALID);
            BudgetResource budget = new BudgetResource();
            budget.setAmount(request.amount()); budget.setRemaining(request.amount()); budget.setConsumed(BigDecimal.ZERO);
            budget.setCurrency(request.currency() == null ? "USD" : request.currency());
            resource = budget;
        } else {
            throw new BadRequestException(ErrorCode.EMPLOYEE_MANAGED_IN_DIRECTORY);
        }
        resource.setSpace(space); resource.setType(request.type()); resource.setName(request.name().trim());
        resource.setStatus("AVAILABLE");
        return response(resources.save(resource));
    }

    @Transactional
    public void delete(UUID spaceId, UUID resourceId, UUID organizationId) {
        Resource resource = resources.findByIdAndSpaceOrganizationId(resourceId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!resource.getSpace().getId().equals(spaceId)) throw new NotFoundException(ErrorCode.RESOURCE_NOT_IN_SPACE);
        if (allocations.existsByResourceId(resourceId))
            throw new ConflictException(ErrorCode.RESOURCE_ASSIGNED_TO_PROJECTS);
        if (resource instanceof HumanResource) throw new BadRequestException(ErrorCode.EMPLOYEE_MANAGED_IN_DIRECTORY);
        resources.delete(resource);
    }

    private Space requireSpace(UUID id, UUID organizationId) {
        return spaces.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new NotFoundException(ErrorCode.SPACE_NOT_FOUND));
    }
    private ResourceResponse response(Resource resource) {
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
    private boolean isProjectAllocationActive(ProjectResourceAllocation allocation) {
        LocalDate today = LocalDate.now();
        return (ScheduleDates.projectStart(allocation.getProject()) == null || !today.isBefore(ScheduleDates.projectStart(allocation.getProject())))
                && (ScheduleDates.projectEnd(allocation.getProject()) == null || !today.isAfter(ScheduleDates.projectEnd(allocation.getProject())));
    }
}
