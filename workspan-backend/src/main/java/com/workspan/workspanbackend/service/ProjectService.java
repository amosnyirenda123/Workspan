package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.ProjectRequest;
import com.workspan.workspanbackend.dto.ProjectResponse;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.ProjectResourceAllocation;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.entity.EquipmentResource;
import com.workspan.workspanbackend.entity.BudgetResource;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projects;
    private final SpaceRepository spaces;
    private final ProjectResourceAllocationRepository allocations;
    private final ScopedRoleProvisioner roleProvisioner;
    private final TaskResourceService taskResourceService;

    public ProjectService(ProjectRepository projects, SpaceRepository spaces, ProjectResourceAllocationRepository allocations, ScopedRoleProvisioner roleProvisioner, TaskResourceService taskResourceService) { this.projects = projects; this.spaces = spaces; this.allocations = allocations; this.roleProvisioner = roleProvisioner; this.taskResourceService = taskResourceService; }

    public List<ProjectResponse> findAll(UUID organizationId) {
        return projects.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public ProjectResponse findById(UUID id, UUID organizationId) { return response(requireProject(id, organizationId)); }

    @Transactional
    public ProjectResponse create(UUID organizationId, UUID creatorId, ProjectRequest request) {
        Space space = requireSpace(request.spaceId(), organizationId);
        validateDates(request.baselineStart(), request.baselineEnd());
        Project project = new Project(space, request.name().trim(), request.description());
        project.setBaselineStart(request.baselineStart()); project.setBaselineEnd(request.baselineEnd());
        Project saved = projects.save(project);
        roleProvisioner.assignProjectOwner(organizationId, creatorId, saved);
        return response(saved);
    }

    @Transactional
    public ProjectResponse update(UUID id, UUID organizationId, ProjectRequest request) {
        Project project = requireProject(id, organizationId);
        Space nextSpace = requireSpace(request.spaceId(), organizationId);
        if (!project.getSpace().getId().equals(nextSpace.getId()) && allocations.existsByProjectId(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove project resources before moving it to another space");
        validateDates(request.baselineStart(), request.baselineEnd());
        validateResourceSchedule(project, ScheduleDates.earliest(request.baselineStart(), project.getStartDate()),
                project.getEndDate() != null ? project.getEndDate() : request.baselineEnd());
        taskResourceService.validateProjectSchedule(project, request.baselineStart(), request.baselineEnd());
        project.setSpace(nextSpace);
        project.setName(request.name().trim()); project.setDescription(request.description());
        project.setBaselineStart(request.baselineStart()); project.setBaselineEnd(request.baselineEnd());
        return response(projects.save(project));
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) { projects.delete(requireProject(id, organizationId)); }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Space requireSpace(UUID id, UUID organizationId) {
        return spaces.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The planned end date cannot be before the planned start date");
    }

    /** nextStart / nextEnd are the project's occupancy window after the change (actual when known, otherwise planned). */
    private void validateResourceSchedule(Project project, LocalDate nextStart, LocalDate nextEnd) {
        List<ProjectResourceAllocation> ownAllocations = allocations.findAllByProjectIdOrderByResourceTypeAscResourceNameAsc(project.getId());
        for (ProjectResourceAllocation own : ownAllocations) {
            List<ProjectResourceAllocation> overlaps = allocations.findAllByResourceId(own.getResource().getId()).stream()
                    .filter(other -> !other.getProject().getId().equals(project.getId())
                            && overlaps(nextStart, nextEnd, ScheduleDates.projectStart(other.getProject()), ScheduleDates.projectEnd(other.getProject()))).toList();
            if (own.getResource() instanceof EquipmentResource equipment) {
                long booked = overlaps.stream().mapToLong(other -> other.getAllocatedQuantity() == null ? 0 : other.getAllocatedQuantity()).sum();
                if (booked + (own.getAllocatedQuantity() == null ? 0 : own.getAllocatedQuantity()) > equipment.getQuantity())
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Project dates exceed equipment availability for " + own.getResource().getName());
            }
        }
    }
    private boolean overlaps(LocalDate start, LocalDate end, LocalDate otherStart, LocalDate otherEnd) {
        return (end == null || otherStart == null || !end.isBefore(otherStart))
                && (otherEnd == null || start == null || !otherEnd.isBefore(start));
    }

    private ProjectResponse response(Project project) {
        return new ProjectResponse(project.getId(), project.getSpace().getId(), project.getName(), project.getDescription(),
                project.getBaselineStart(), project.getBaselineEnd(), project.getStartDate(), project.getEndDate(), project.getCreatedAt());
    }
}
