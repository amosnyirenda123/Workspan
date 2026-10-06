package com.ilot.ilotbackend.service;

import com.ilot.ilotbackend.entity.Organization;
import com.ilot.ilotbackend.dto.OrganizationRequest;
import com.ilot.ilotbackend.dto.OrganizationResponse;
import com.ilot.ilotbackend.mapper.OrganizationMapper;
import com.ilot.ilotbackend.repository.*;
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

    public List<OrganizationResponse> findAll() {
        return organizations.findAll().stream().map(this::response).toList();
    }

    public OrganizationResponse findById(UUID id) {
        return response(organizations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found")));
    }

    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        String name = request.name().trim();
        if (organizations.existsByNameIgnoreCase(name))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization with this name already exists");
        OrganizationRequest normalized = new OrganizationRequest(name, request.description(), request.website(), request.contactEmail(), request.phone());
        return response(organizations.save(mapper.toEntity(normalized)));
    }

    private OrganizationResponse response(Organization organization) {
        UUID id = organization.getId();
        return mapper.toResponse(organization, spaces.countByOrganizationId(id), projects.countBySpaceOrganizationId(id), tasks.countByProjectSpaceOrganizationId(id));
    }
}
