package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.TaskRequest;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.dto.TaskResourceAllocationRequest;
import com.workspan.workspanbackend.dto.TaskResourceAllocationResponse;
import com.workspan.workspanbackend.dto.TaskResourceOptionResponse;
import com.workspan.workspanbackend.service.TaskService;
import com.workspan.workspanbackend.service.TaskResourceService;
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
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Tâches rattachées aux projets")
@SecurityRequirement(name = "sessionCookie")
public class TaskController {
    private final TaskService tasks;
    private final TaskResourceService taskResources;
    private final WorkspaceAccess access;
    public TaskController(TaskService tasks, TaskResourceService taskResources, WorkspaceAccess access) { this.tasks = tasks; this.taskResources = taskResources; this.access = access; }

    @GetMapping("/{id}/resources")
    public List<TaskResourceAllocationResponse> findResources(@PathVariable UUID id, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectAnyPermission(session, task.projectId(), List.of("project.view", "project.resources.manage"));
        return taskResources.findAssigned(id, organizationId);
    }

    @GetMapping("/{id}/resource-pool")
    public List<TaskResourceOptionResponse> findResourcePool(@PathVariable UUID id, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectAnyPermission(session, task.projectId(), List.of("project.view", "project.resources.manage"));
        return taskResources.findPool(id, organizationId);
    }

    @PostMapping("/{id}/resources") @ResponseStatus(HttpStatus.CREATED)
    public TaskResourceAllocationResponse assignResource(@PathVariable UUID id, @Valid @RequestBody TaskResourceAllocationRequest request, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectPermission(session, task.projectId(), "project.resources.manage");
        return taskResources.assign(id, organizationId, request);
    }

    @DeleteMapping("/{id}/resources/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeResource(@PathVariable UUID id, @PathVariable UUID resourceId, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectPermission(session, task.projectId(), "project.resources.manage");
        taskResources.remove(id, organizationId, resourceId);
    }

    @GetMapping @Operation(summary = "Lister les tâches de l’organisation")
    public List<TaskResponse> findAll(HttpSession session) {
        UUID userId = access.userId(session); UUID organizationId = access.organizationId(session);
        return tasks.findAll(organizationId).stream().filter(task -> java.util.List.of("task.view", "task.edit", "task.delete", "project.resources.manage")
                .stream().anyMatch(key -> access.hasProjectPermissionById(userId, organizationId, task.projectId(), key))).toList();
    }

    @GetMapping("/{id}") @Operation(summary = "Lire une tâche")
    public TaskResponse findById(@PathVariable UUID id, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectAnyPermission(session, task.projectId(), java.util.List.of("task.view", "task.edit", "task.delete", "project.resources.manage")); return task;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer une tâche")
    public TaskResponse create(@Valid @RequestBody TaskRequest request, HttpSession session) { return tasks.create(access.requireProjectPermission(session, request.projectId(), "task.create"), request); }

    @PutMapping("/{id}") @Operation(summary = "Modifier une tâche")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse current = tasks.findById(id, organizationId);
        access.requireProjectPermission(session, current.projectId(), "task.edit");
        if (!current.projectId().equals(request.projectId())) access.requireProjectPermission(session, request.projectId(), "task.create");
        return tasks.update(id, organizationId, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer une tâche")
    public void delete(@PathVariable UUID id, HttpSession session) {
        UUID organizationId = access.organizationId(session); TaskResponse task = tasks.findById(id, organizationId);
        access.requireProjectPermission(session, task.projectId(), "task.delete"); tasks.delete(id, organizationId);
    }
}
