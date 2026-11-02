package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.ResourceRequest;
import com.workspan.workspanbackend.dto.ResourceResponse;
import com.workspan.workspanbackend.service.ResourceService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces/{spaceId}/resources")
public class ResourceController {
    private final ResourceService resources;
    private final WorkspaceAccess access;
    public ResourceController(ResourceService resources, WorkspaceAccess access) { this.resources = resources; this.access = access; }

    @GetMapping public List<ResourceResponse> findPool(@PathVariable UUID spaceId, HttpSession session) {
        return resources.findSpacePool(spaceId, access.requireSpaceAnyPermission(session, spaceId, List.of("resource.pool.manage", "project.resources.manage")));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@PathVariable UUID spaceId, @Valid @RequestBody ResourceRequest request, HttpSession session) {
        return resources.create(spaceId, access.requireSpacePermission(session, spaceId, "resource.pool.manage"), request);
    }
    @DeleteMapping("/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID spaceId, @PathVariable UUID resourceId, HttpSession session) {
        resources.delete(spaceId, resourceId, access.requireSpacePermission(session, spaceId, "resource.pool.manage"));
    }
}
