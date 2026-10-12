package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.ProjectRequest;
import com.workspan.workspanbackend.dto.ProjectResponse;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projects;
    private final SpaceRepository spaces;

    public ProjectService(ProjectRepository projects, SpaceRepository spaces) { this.projects = projects; this.spaces = spaces; }

    public List<ProjectResponse> findAll(UUID organizationId) {
        return projects.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public ProjectResponse findById(UUID id, UUID organizationId) { return response(requireProject(id, organizationId)); }

    @Transactional
    public ProjectResponse create(UUID organizationId, ProjectRequest request) {
        Space space = requireSpace(request.spaceId(), organizationId);
        validateDates(request.startDate(), request.endDate());
        Project project = new Project(space, request.name().trim(), request.description());
        project.setStartDate(request.startDate()); project.setEndDate(request.endDate());
        return response(projects.save(project));
    }

    @Transactional
    public ProjectResponse update(UUID id, UUID organizationId, ProjectRequest request) {
        Project project = requireProject(id, organizationId);
        project.setSpace(requireSpace(request.spaceId(), organizationId));
        project.setName(request.name().trim()); project.setDescription(request.description());
        validateDates(request.startDate(), request.endDate());
        project.setStartDate(request.startDate()); project.setEndDate(request.endDate());
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
    }

    private ProjectResponse response(Project project) {
        return new ProjectResponse(project.getId(), project.getSpace().getId(), project.getName(), project.getDescription(), project.getStartDate(), project.getEndDate(), project.getCreatedAt());
    }
}
