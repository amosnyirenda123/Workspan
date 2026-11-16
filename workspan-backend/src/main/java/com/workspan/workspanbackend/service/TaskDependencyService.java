package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.TaskDependencyRequest;
import com.workspan.workspanbackend.dto.TaskDependencyResponse;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskDependency;
import com.workspan.workspanbackend.repository.TaskDependencyRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dependencies between tasks of the same project. Rules: both tasks belong to the same project, a task cannot depend on
 * itself, a pair can only be linked once, and the dependency graph must stay acyclic.
 */
@Service
@Transactional(readOnly = true)
public class TaskDependencyService {
    private final TaskDependencyRepository dependencies;
    private final TaskRepository tasks;

    public TaskDependencyService(TaskDependencyRepository dependencies, TaskRepository tasks) {
        this.dependencies = dependencies; this.tasks = tasks;
    }

    public List<TaskDependencyResponse> findByProject(UUID projectId) {
        return dependencies.findAllByPredecessorTaskProjectId(projectId).stream().map(this::response).toList();
    }

    /** Project that owns the dependency (used for permission checks). */
    public UUID projectOf(UUID dependencyId, UUID organizationId) {
        return require(dependencyId, organizationId).getPredecessorTask().getProject().getId();
    }

    @Transactional
    public TaskDependencyResponse create(UUID projectId, UUID organizationId, TaskDependencyRequest request) {
        Task predecessor = requireTask(request.predecessorTaskId(), organizationId);
        Task successor = requireTask(request.successorTaskId(), organizationId);
        validate(projectId, predecessor, successor, null);
        if (dependencies.existsByPredecessorTaskIdAndSuccessorTaskId(predecessor.getId(), successor.getId())
                || dependencies.existsByPredecessorTaskIdAndSuccessorTaskId(successor.getId(), predecessor.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "These tasks are already linked");
        TaskDependency dependency = new TaskDependency();
        dependency.setPredecessorTask(predecessor);
        dependency.setSuccessorTask(successor);
        dependency.setType(request.type());
        dependency.setLag(request.lag() == null ? 0 : request.lag());
        return response(dependencies.save(dependency));
    }

    @Transactional
    public TaskDependencyResponse update(UUID dependencyId, UUID organizationId, TaskDependencyRequest request) {
        TaskDependency dependency = require(dependencyId, organizationId);
        UUID projectId = dependency.getPredecessorTask().getProject().getId();
        Task predecessor = requireTask(request.predecessorTaskId(), organizationId);
        Task successor = requireTask(request.successorTaskId(), organizationId);
        boolean endpointsChanged = !predecessor.getId().equals(dependency.getPredecessorTask().getId())
                || !successor.getId().equals(dependency.getSuccessorTask().getId());
        if (endpointsChanged) {
            validate(projectId, predecessor, successor, dependency.getId());
            if (dependencies.findAllByPredecessorTaskProjectId(projectId).stream().anyMatch(other -> !other.getId().equals(dependency.getId())
                    && (sameEdge(other, predecessor, successor) || sameEdge(other, successor, predecessor))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "These tasks are already linked");
        }
        dependency.setPredecessorTask(predecessor);
        dependency.setSuccessorTask(successor);
        dependency.setType(request.type());
        dependency.setLag(request.lag() == null ? 0 : request.lag());
        return response(dependencies.save(dependency));
    }

    @Transactional
    public void delete(UUID dependencyId, UUID organizationId) {
        dependencies.delete(require(dependencyId, organizationId));
    }

    private void validate(UUID projectId, Task predecessor, Task successor, UUID ignoredDependencyId) {
        if (predecessor.getId().equals(successor.getId())) throw badRequest("A task cannot depend on itself");
        if (!predecessor.getProject().getId().equals(projectId) || !successor.getProject().getId().equals(projectId))
            throw badRequest("Both tasks must belong to the same project");
        if (createsCycle(projectId, predecessor.getId(), successor.getId(), ignoredDependencyId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This dependency would create a cycle");
    }

    /** A new edge pred -> succ creates a cycle when pred is already reachable from succ. */
    private boolean createsCycle(UUID projectId, UUID predecessorId, UUID successorId, UUID ignoredDependencyId) {
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (TaskDependency dependency : dependencies.findAllByPredecessorTaskProjectId(projectId)) {
            if (dependency.getId().equals(ignoredDependencyId)) continue;
            graph.computeIfAbsent(dependency.getPredecessorTask().getId(), key -> new java.util.ArrayList<>()).add(dependency.getSuccessorTask().getId());
        }
        Deque<UUID> stack = new ArrayDeque<>(List.of(successorId));
        Set<UUID> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (current.equals(predecessorId)) return true;
            if (seen.add(current)) stack.addAll(graph.getOrDefault(current, List.of()));
        }
        return false;
    }

    private static boolean sameEdge(TaskDependency dependency, Task predecessor, Task successor) {
        return dependency.getPredecessorTask().getId().equals(predecessor.getId()) && dependency.getSuccessorTask().getId().equals(successor.getId());
    }

    private TaskDependency require(UUID id, UUID organizationId) {
        return dependencies.findByIdAndPredecessorTaskProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dependency not found"));
    }

    private Task requireTask(UUID id, UUID organizationId) {
        return tasks.findByIdAndProjectSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private TaskDependencyResponse response(TaskDependency dependency) {
        return new TaskDependencyResponse(dependency.getId(), dependency.getPredecessorTask().getId(), dependency.getPredecessorTask().getName(),
                dependency.getSuccessorTask().getId(), dependency.getSuccessorTask().getName(), dependency.getType(),
                dependency.getLag() == null ? 0 : dependency.getLag());
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
