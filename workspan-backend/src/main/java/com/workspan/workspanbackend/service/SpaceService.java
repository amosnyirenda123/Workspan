package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.SpaceRequest;
import com.workspan.workspanbackend.dto.SpaceResponse;
import com.workspan.workspanbackend.entity.Organization;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.repository.OrganizationRepository;
import com.workspan.workspanbackend.repository.SpaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SpaceService {
    private final SpaceRepository spaces;
    private final OrganizationRepository organizations;
    private final ScopedRoleProvisioner roleProvisioner;

    public SpaceService(SpaceRepository spaces, OrganizationRepository organizations, ScopedRoleProvisioner roleProvisioner) {
        this.spaces = spaces; this.organizations = organizations; this.roleProvisioner = roleProvisioner;
    }

    public List<SpaceResponse> findAll(UUID organizationId) {
        return spaces.findAllByOrganizationIdOrderByNameAsc(organizationId).stream().map(this::response).toList();
    }

    public SpaceResponse findById(UUID id, UUID organizationId) { return response(requireSpace(id, organizationId)); }

    @Transactional
    public SpaceResponse create(UUID organizationId, UUID creatorId, SpaceRequest request) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        Space space = spaces.save(new Space(organization, request.name().trim()));
        roleProvisioner.assignSpaceOwner(organizationId, creatorId, space);
        return response(space);
    }

    @Transactional
    public SpaceResponse update(UUID id, UUID organizationId, SpaceRequest request) {
        Space space = requireSpace(id, organizationId);
        space.setName(request.name().trim());
        return response(spaces.save(space));
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) { spaces.delete(requireSpace(id, organizationId)); }

    private Space requireSpace(UUID id, UUID organizationId) {
        return spaces.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
    }

    private SpaceResponse response(Space space) {
        return new SpaceResponse(space.getId(), space.getOrganization().getId(), space.getName(), space.getCreatedAt());
    }
}
