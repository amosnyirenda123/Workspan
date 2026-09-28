package com.ilot.ilotbackend.mapper;
import com.ilot.ilotbackend.domain.Organization;
import com.ilot.ilotbackend.dto.OrganizationRequest;
import com.ilot.ilotbackend.dto.OrganizationResponse;
import org.springframework.stereotype.Component;
@Component
public class OrganizationMapper {
    public Organization toEntity(OrganizationRequest request) { return new Organization(request.name().trim()); }
    public OrganizationResponse toResponse(Organization entity, long spaces, long projects, long tasks) {
        return new OrganizationResponse(entity.getId(), entity.getName(), spaces, projects, tasks, entity.getCreatedAt());
    }
}
