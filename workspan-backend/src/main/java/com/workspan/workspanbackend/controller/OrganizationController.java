package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.OrganizationResponse;
import com.workspan.workspanbackend.service.OrganizationService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organizations", description = "Organisation associée à la session")
@SecurityRequirement(name = "sessionCookie")
public class OrganizationController {
    private final OrganizationService service;
    private final WorkspaceAccess access;

    public OrganizationController(OrganizationService service, WorkspaceAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<OrganizationResponse> findAll(HttpSession session) {
        return service.findForOrganization(access.organizationId(session));
    }

    @GetMapping("/{id}")
    public OrganizationResponse findById(@PathVariable UUID id, HttpSession session) {
        return service.findById(id, access.organizationId(session));
    }
}
