package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.Organization;
import com.workspan.workspanbackend.dto.OrganizationResponse;
import com.workspan.workspanbackend.mapper.OrganizationMapper;
import com.workspan.workspanbackend.repository.*;
import com.workspan.workspanbackend.repository.OrganizationRepository;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final SpaceRepository spaces;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final OrganizationMapper mapper;

    public OrganizationService(OrganizationRepository organizations, SpaceRepository spaces, ProjectRepository projects, TaskRepository tasks, OrganizationMapper mapper) {
        this.organizations = organizations;
        this.spaces = spaces;
        this.projects = projects;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    public List<OrganizationResponse> findForOrganization(UUID organizationId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        return List.of(response(organization));
    }

    private OrganizationResponse findById(UUID id) {
        return response(organizations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found")));
    }

    public OrganizationResponse findById(UUID id, UUID organizationId) {
        if (!id.equals(organizationId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        return findById(id);
    }

    private OrganizationResponse response(Organization organization) {
        UUID id = organization.getId();
        return mapper.toResponse(organization, spaces.countByOrganizationId(id), projects.countBySpaceOrganizationId(id), tasks.countByProjectSpaceOrganizationId(id));
    }
}
