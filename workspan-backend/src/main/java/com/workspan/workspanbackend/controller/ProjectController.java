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
    public List<ProjectResponse> findAll(HttpSession session) { return projects.findAll(access.organizationId(session)); }

    @GetMapping("/{id}") @Operation(summary = "Lire un projet")
    public ProjectResponse findById(@PathVariable UUID id, HttpSession session) { return projects.findById(id, access.organizationId(session)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer un projet")
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request, HttpSession session) { return projects.create(access.organizationId(session), request); }

    @PutMapping("/{id}") @Operation(summary = "Modifier un projet")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request, HttpSession session) { return projects.update(id, access.organizationId(session), request); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer un projet")
    public void delete(@PathVariable UUID id, HttpSession session) { projects.delete(id, access.organizationId(session)); }
}
