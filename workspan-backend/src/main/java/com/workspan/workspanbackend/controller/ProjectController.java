package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.ProjectRequest;
import com.workspan.workspanbackend.dto.ProjectResponse;
import com.workspan.workspanbackend.service.ProjectService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Projets organisés dans les espaces")
@SecurityRequirement(name = "sessionCookie")
public class ProjectController {
    private final ProjectService projects;
    private final WorkspaceAccess access;
    public ProjectController(ProjectService projects, WorkspaceAccess access) { this.projects = projects; this.access = access; }

    @GetMapping @Operation(summary = "Lister les projets de l’organisation")
    public List<ProjectResponse> findAll(HttpSession session) {
        UUID userId = access.userId(session); UUID organizationId = access.organizationId(session);
        return projects.findAll(organizationId).stream().filter(project -> java.util.List.of("project.view", "project.edit", "project.delete", "project.resources.manage", "task.view", "task.create", "task.edit", "task.delete", "access.manage")
                .stream().anyMatch(key -> access.hasProjectPermission(userId, organizationId, project.spaceId(), project.id(), key))).toList();
    }

    @GetMapping("/{id}") @Operation(summary = "Lire un projet")
    public ProjectResponse findById(@PathVariable UUID id, HttpSession session) { return projects.findById(id, access.requireProjectAnyPermission(session, id, java.util.List.of("project.view", "project.edit", "project.delete"))); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer un projet")
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request, HttpSession session) { return projects.create(access.requireSpacePermission(session, request.spaceId(), "project.create"), access.userId(session), request); }

    @PutMapping("/{id}") @Operation(summary = "Modifier un projet")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request, HttpSession session) {
        UUID organizationId = access.requireProjectPermission(session, id, "project.edit");
        ProjectResponse existing = projects.findById(id, organizationId);
        if (!existing.spaceId().equals(request.spaceId())) access.requireSpacePermission(session, request.spaceId(), "project.create");
        return projects.update(id, organizationId, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer un projet")
    public void delete(@PathVariable UUID id, HttpSession session) { projects.delete(id, access.requireProjectPermission(session, id, "project.delete")); }
}
