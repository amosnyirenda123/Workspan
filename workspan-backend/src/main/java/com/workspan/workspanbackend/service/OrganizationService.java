package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.entity.Organization;
import com.workspan.workspanbackend.dto.OrganizationResponse;
import com.workspan.workspanbackend.dto.OrganizationUpdateRequest;
import com.workspan.workspanbackend.mapper.OrganizationMapper;
import com.workspan.workspanbackend.repository.*;
import com.workspan.workspanbackend.repository.OrganizationRepository;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
        return List.of(response(organization));
    }

    private OrganizationResponse findById(UUID id) {
        return response(organizations.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND)));
    }

    public OrganizationResponse findById(UUID id, UUID organizationId) {
        if (!id.equals(organizationId)) throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
        return findById(id);
    }

    @Transactional
    public OrganizationResponse update(UUID id, UUID organizationId, OrganizationUpdateRequest request) {
        if (!id.equals(organizationId)) throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
        Organization organization = organizations.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
        String name = request.name().trim();
        if (organizations.existsByNameIgnoreCaseAndIdNot(name, id))
            throw new ConflictException(ErrorCode.ORGANIZATION_NAME_TAKEN);
        organization.setName(name);
        organization.setDescription(blankToNull(request.description()));
        organization.setWebsite(blankToNull(request.website()));
        organization.setContactEmail(blankToNull(request.contactEmail()));
        organization.setPhone(blankToNull(request.phone()));
        return response(organizations.save(organization));
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private OrganizationResponse response(Organization organization) {
        UUID id = organization.getId();
        return mapper.toResponse(organization, spaces.countByOrganizationId(id), projects.countBySpaceOrganizationId(id), tasks.countByProjectSpaceOrganizationId(id));
    }
}
