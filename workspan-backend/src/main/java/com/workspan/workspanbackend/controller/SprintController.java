package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.SprintRequest;
import com.workspan.workspanbackend.dto.SprintResponse;
import com.workspan.workspanbackend.service.SprintService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sprints")
@Tag(name = "Sprints", description = "Planning et cycle de vie des sprints Scrum")
@SecurityRequirement(name = "sessionCookie")
public class SprintController {
    private final SprintService sprints;
    private final WorkspaceAccess access;
    public SprintController(SprintService sprints, WorkspaceAccess access) { this.sprints = sprints; this.access = access; }

    @GetMapping("/projects/{projectId}")
    public List<SprintResponse> list(@PathVariable UUID projectId, HttpSession session) {
        return sprints.list(projectId, access.requireProjectPermission(session, projectId, "task.view.scrum"));
    }

    @PostMapping("/projects/{projectId}") @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse create(@PathVariable UUID projectId, @Valid @RequestBody SprintRequest request, HttpSession session) {
        return sprints.create(projectId, access.requireProjectPermission(session, projectId, "task.sprint.manage"), request);
    }

    @PutMapping("/{sprintId}")
    public SprintResponse update(@PathVariable UUID sprintId, @Valid @RequestBody SprintRequest request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, sprints.projectId(sprintId, organizationId), "task.sprint.manage");
        return sprints.update(sprintId, organizationId, request);
    }

    @DeleteMapping("/{sprintId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID sprintId, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, sprints.projectId(sprintId, organizationId), "task.sprint.manage");
        sprints.delete(sprintId, organizationId);
    }

    @PutMapping("/{sprintId}/tasks/{taskId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTask(@PathVariable UUID sprintId, @PathVariable UUID taskId, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        UUID projectId = sprints.projectId(sprintId, organizationId);
        access.requireProjectPermission(session, projectId, "task.sprint.manage");
        access.requireProjectPermission(session, projectId, "task.edit");
        sprints.addTask(sprintId, taskId, organizationId);
    }

    @DeleteMapping("/{sprintId}/tasks/{taskId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTask(@PathVariable UUID sprintId, @PathVariable UUID taskId, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        UUID projectId = sprints.projectId(sprintId, organizationId);
        access.requireProjectPermission(session, projectId, "task.sprint.manage");
        access.requireProjectPermission(session, projectId, "task.edit");
        sprints.removeTask(sprintId, taskId, organizationId);
    }
}
