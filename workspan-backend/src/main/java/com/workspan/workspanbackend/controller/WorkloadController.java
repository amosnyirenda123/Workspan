package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.LevelingProposal;
import com.workspan.workspanbackend.dto.WorkloadRequests;
import com.workspan.workspanbackend.dto.WorkloadResponse;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.service.WorkloadService;
import com.workspan.workspanbackend.service.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resource workload (charge) and redistribution. Permissions:
 * workload.view (read the analysis), workload.manage (reassign, shift, leveling). Reassigning to an employee who is
 * not yet in the project pool also adds them to it and therefore also requires project.resources.manage.
 */
@RestController
@RequestMapping("/api/workload")
@Tag(name = "Workload", description = "Charge des ressources, surcharges et redistribution")
@SecurityRequirement(name = "sessionCookie")
public class WorkloadController {
    private static final List<String> PROJECT_READ_KEYS = List.of("project.view", "task.view", "gantt.view", "workload.view");

    private final WorkloadService workload;
    private final WorkspaceAccess access;
    private final ProjectRepository projects;

    public WorkloadController(WorkloadService workload, WorkspaceAccess access, ProjectRepository projects) {
        this.workload = workload; this.access = access; this.projects = projects;
    }

    @GetMapping("/spaces/{spaceId}") @Operation(summary = "Charge des ressources d'un espace")
    public WorkloadResponse space(@PathVariable UUID spaceId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  HttpSession session) {
        access.requireSpacePermission(session, spaceId, "workload.view");
        return workload.analyse(spaceId, from, to, null, visibleProjects(session, spaceId));
    }

    @GetMapping("/projects/{projectId}") @Operation(summary = "Charge des ressources du pool d'un projet (tous projets confondus)")
    public WorkloadResponse project(@PathVariable UUID projectId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    HttpSession session) {
        UUID organizationId = access.requireProjectPermission(session, projectId, "workload.view");
        UUID spaceId = workload.spaceOfProject(projectId, organizationId);
        return workload.analyse(spaceId, from, to, workload.projectPoolResourceIds(projectId), visibleProjects(session, spaceId));
    }

    @GetMapping("/spaces/{spaceId}/leveling") @Operation(summary = "Proposition de lissage de charge pour un espace")
    public LevelingProposal spaceLeveling(@PathVariable UUID spaceId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          HttpSession session) {
        access.requireSpacePermission(session, spaceId, "workload.manage");
        return workload.leveling(spaceId, from, to, null, visibleProjects(session, spaceId));
    }

    @GetMapping("/projects/{projectId}/leveling") @Operation(summary = "Proposition de lissage limitée aux tâches d'un projet")
    public LevelingProposal projectLeveling(@PathVariable UUID projectId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            HttpSession session) {
        UUID organizationId = access.requireProjectPermission(session, projectId, "workload.manage");
        UUID spaceId = workload.spaceOfProject(projectId, organizationId);
        return workload.leveling(spaceId, from, to, projectId, visibleProjects(session, spaceId));
    }

    @PostMapping("/leveling/apply") @Operation(summary = "Appliquer les actions de lissage sélectionnées")
    public Map<String, Integer> applyLeveling(@Valid @RequestBody WorkloadRequests.ApplyLeveling request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        for (WorkloadRequests.LevelingAction action : request.actions()) {
            UUID projectId = workload.projectOfTask(action.taskId(), organizationId);
            access.requireProjectPermission(session, projectId, "workload.manage");
            if ("REASSIGN".equals(action.type()) && action.toResourceId() != null && workload.requiresPoolAddition(action.taskId(), action.toResourceId(), organizationId))
                access.requireProjectPermission(session, projectId, "project.resources.manage");
        }
        return Map.of("applied", workload.applyLeveling(organizationId, request.actions()));
    }

    @GetMapping("/tasks/{taskId}/resources/{resourceId}/candidates") @Operation(summary = "Employés pouvant reprendre une affectation")
    public List<WorkloadRequests.Candidate> candidates(@PathVariable UUID taskId, @PathVariable UUID resourceId, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, workload.projectOfTask(taskId, organizationId), "workload.manage");
        return workload.candidates(taskId, resourceId, organizationId);
    }

    @PostMapping("/reassign") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Réaffecter une tâche à un autre employé")
    public void reassign(@Valid @RequestBody WorkloadRequests.Reassign request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        UUID projectId = workload.projectOfTask(request.taskId(), organizationId);
        access.requireProjectPermission(session, projectId, "workload.manage");
        if (workload.requiresPoolAddition(request.taskId(), request.toResourceId(), organizationId))
            access.requireProjectPermission(session, projectId, "project.resources.manage");
        workload.reassign(organizationId, request);
    }

    @PostMapping("/shift") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Décaler une tâche non démarrée dans sa marge")
    public void shift(@Valid @RequestBody WorkloadRequests.Shift request, HttpSession session) {
        UUID organizationId = access.organizationId(session);
        access.requireProjectPermission(session, workload.projectOfTask(request.taskId(), organizationId), "workload.manage");
        workload.shift(organizationId, request);
    }

    /** Projects of the space whose task names the current user may see (other assignments are anonymised). */
    private Set<UUID> visibleProjects(HttpSession session, UUID spaceId) {
        UUID userId = access.userId(session);
        UUID organizationId = access.organizationId(session);
        return projects.findAllBySpaceIdOrderByNameAsc(spaceId).stream()
                .filter(project -> PROJECT_READ_KEYS.stream().anyMatch(key -> access.hasProjectPermission(userId, organizationId, spaceId, project.getId(), key)))
                .map(project -> project.getId()).collect(Collectors.toSet());
    }
}
