package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.ProjectResourceAllocationRequest;
import com.workspan.workspanbackend.dto.ProjectResourceAllocationResponse;
import com.workspan.workspanbackend.dto.ResourceResponse;
import com.workspan.workspanbackend.service.ProjectResourceService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectResourceController {
    private final ProjectResourceService resources;
    private final WorkspaceAccess access;
    public ProjectResourceController(ProjectResourceService resources, WorkspaceAccess access) { this.resources = resources; this.access = access; }

    @GetMapping("/resource-pool")
    public List<ResourceResponse> findPool(@PathVariable UUID projectId, HttpSession session) {
        return resources.findPool(projectId, access.requireProjectAnyPermission(session, projectId, List.of("project.view", "project.resources.manage")));
    }
    @GetMapping("/resources")
    public List<ProjectResourceAllocationResponse> findAssigned(@PathVariable UUID projectId, HttpSession session) {
        return resources.findAssigned(projectId, access.requireProjectAnyPermission(session, projectId, List.of("project.view", "project.resources.manage", "analytics.view")));
    }
    @PostMapping("/resources") @ResponseStatus(HttpStatus.CREATED)
    public ProjectResourceAllocationResponse assign(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectResourceAllocationRequest request, HttpSession session) {
        return resources.assign(projectId, access.requireProjectPermission(session, projectId, "project.resources.manage"), request);
    }
    @DeleteMapping("/resources/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID projectId, @PathVariable UUID resourceId, HttpSession session) {
        resources.remove(projectId, access.requireProjectPermission(session, projectId, "project.resources.manage"), resourceId);
    }
}
