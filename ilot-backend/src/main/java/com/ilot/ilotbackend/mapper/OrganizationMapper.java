package com.ilot.ilotbackend.mapper;
import com.ilot.ilotbackend.domain.Organization;
import com.ilot.ilotbackend.dto.OrganizationRequest;
import com.ilot.ilotbackend.dto.OrganizationResponse;
import org.springframework.stereotype.Component;
@Component
public class OrganizationMapper {
    public Organization toEntity(OrganizationRequest request) {
        Organization organization = new Organization(request.name().trim());
        organization.setDescription(clean(request.description()));
        organization.setWebsite(clean(request.website()));
        organization.setContactEmail(clean(request.contactEmail()));
        organization.setPhone(clean(request.phone()));
        return organization;
    }
    public OrganizationResponse toResponse(Organization entity, long spaces, long projects, long tasks) {
        return new OrganizationResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.getWebsite(), entity.getContactEmail(), entity.getPhone(), spaces, projects, tasks, entity.getCreatedAt());
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
