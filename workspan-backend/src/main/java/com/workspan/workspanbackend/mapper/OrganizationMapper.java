package com.workspan.workspanbackend.mapper;

import com.workspan.workspanbackend.entity.Organization;
import com.workspan.workspanbackend.dto.OrganizationResponse;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {
    public OrganizationResponse toResponse(Organization entity, long spaces, long projects, long tasks) {
        return new OrganizationResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.getWebsite(), entity.getContactEmail(), entity.getPhone(), spaces, projects, tasks, entity.getCreatedAt());
    }
}
