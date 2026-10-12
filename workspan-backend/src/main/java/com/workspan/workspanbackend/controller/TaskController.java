package com.workspan.workspanbackend.controller;

import com.workspan.workspanbackend.dto.TaskRequest;
import com.workspan.workspanbackend.dto.TaskResponse;
import com.workspan.workspanbackend.service.TaskService;
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
    private final WorkspaceAccess access;
    public TaskController(TaskService tasks, WorkspaceAccess access) { this.tasks = tasks; this.access = access; }

    @GetMapping @Operation(summary = "Lister les tâches de l’organisation")
    public List<TaskResponse> findAll(HttpSession session) { return tasks.findAll(access.organizationId(session)); }

    @GetMapping("/{id}") @Operation(summary = "Lire une tâche")
    public TaskResponse findById(@PathVariable UUID id, HttpSession session) { return tasks.findById(id, access.organizationId(session)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Créer une tâche")
    public TaskResponse create(@Valid @RequestBody TaskRequest request, HttpSession session) { return tasks.create(access.organizationId(session), request); }

    @PutMapping("/{id}") @Operation(summary = "Modifier une tâche")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request, HttpSession session) { return tasks.update(id, access.organizationId(session), request); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Supprimer une tâche")
    public void delete(@PathVariable UUID id, HttpSession session) { tasks.delete(id, access.organizationId(session)); }
}
