package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.GanttResponse;
import com.workspan.workspanbackend.dto.RescheduleResponse;
import com.workspan.workspanbackend.dto.TaskDependencyRequest;
import com.workspan.workspanbackend.dto.TaskDependencyResponse;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.dto.TaskScheduleRequest;
import com.workspan.workspanbackend.service.GanttService;
import com.workspan.workspanbackend.service.TaskDependencyService;
import com.workspan.workspanbackend.service.TaskService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import com.workspan.workspanbackend.service.TaskActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gantt chart and scheduling. Permissions:
 * gantt.view (read chart and dependencies), gantt.dependencies.manage (create/update/delete dependencies),
 * gantt.schedule.manage (edit dates/progress from the chart, automatic rescheduling).
 */
@RestController
@RequestMapping("/api/gantt")
@Tag(name = "Gantt", description = "Diagramme de Gantt, dépendances et analyse de planification")
@SecurityRequirement(name = "sessionCookie")
public class GanttController {
    private final GanttService gantt;
    private final TaskDependencyService dependencies;
    private final TaskService tasks;
    private final WorkspaceAccess access;
    private final TaskActivityService activity;

    public GanttController(GanttService gantt, TaskDependencyService dependencies, TaskService tasks, WorkspaceAccess access, TaskActivityService activity) {
        this.gantt = gantt; this.dependencies = dependencies; this.tasks = tasks; this.access = access; this.activity = activity;
    }

    @GetMapping("/projects/{projectId}") @Operation(summary = "Diagramme de Gantt calculé d'un projet")
    public GanttResponse project(@PathVariable UUID projectId, HttpSession session) {
        return gantt.gantt(projectId, access.requireProjectPermission(session, projectId, "gantt.view"));
    }

    @PostMapping("/projects/{projectId}/reschedule") @Operation(summary = "Replanifier automatiquement les tâches non démarrées")
    @Transactional
    public RescheduleResponse reschedule(@PathVariable UUID projectId, HttpSession session) {
        UUID organizationId = access.requireProjectPermission(session, projectId, "gantt.schedule.manage");
        RescheduleResponse result = gantt.reschedule(projectId, organizationId);
        UUID actorId = access.userId(session);
        result.moves().forEach(move -> activity.record(move.taskId(), projectId, actorId, "SCHEDULE_CHANGED", "Rescheduled " + move.name() + " to " + move.newStart() + "–" + move.newEnd()));
        return result;
    }

    @GetMapping("/projects/{projectId}/dependencies") @Operation(summary = "Lister les dépendances d'un projet")
    public List<TaskDependencyResponse> listDependencies(@PathVariable UUID projectId, HttpSession session) {
        access.requireProjectPermission(session, projectId, "gantt.view");
        return dependencies.findByProject(projectId);
    }

    @PostMapping("/projects/{projectId}/dependencies") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer une dépendance")
    public TaskDependencyResponse createDependency(@PathVariable UUID projectId, @Valid @RequestBody TaskDependencyRequest request, HttpSession session) {
        return dependencies.create(projectId, access.requireProjectPermission(session, projectId, "gantt.dependencies.manage"), request);
    }

    @PutMapping("/dependencies/{dependencyId}") @Operation(summary = "Modifier une dépendance (type, décalage)")
    public TaskDependencyResponse updateDependency(@PathVariable UUID dependencyId, @Valid @RequestBody TaskDependencyRequest request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, dependencies.projectOf(dependencyId, organizationId), "gantt.dependencies.manage");
        return dependencies.update(dependencyId, organizationId, request);
    }

    @DeleteMapping("/dependencies/{dependencyId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer une dépendance")
    public void deleteDependency(@PathVariable UUID dependencyId, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, dependencies.projectOf(dependencyId, organizationId), "gantt.dependencies.manage");
        dependencies.delete(dependencyId, organizationId);
    }

    @PutMapping("/tasks/{taskId}/schedule") @Operation(summary = "Modifier les dates planifiées/réelles et l'avancement d'une tâche")
    @Transactional
    public TaskResponse updateSchedule(@PathVariable UUID taskId, @Valid @RequestBody TaskScheduleRequest request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        TaskResponse task = tasks.findById(taskId, organizationId);
        access.requireProjectPermission(session, task.projectId(), "gantt.schedule.manage");
        TaskResponse updated = tasks.updateSchedule(taskId, organizationId, request);
        activity.record(updated.id(), updated.projectId(), access.userId(session), "SCHEDULE_CHANGED", "Updated schedule for " + updated.name());
        return updated;
    }
}
