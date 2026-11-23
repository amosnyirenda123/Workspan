package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.RoleScope;

import java.util.List;
import java.util.Set;

/**
 * The single catalogue of permissions. Every backend function is guarded by one of these keys, and the
 * "Rôles et permissions" section builds roles from this list. The scopes at which a permission may be
 * granted are defined here too, so the backend validation and the frontend picker never drift apart.
 */
public final class PermissionCatalog {
    private PermissionCatalog() {}
    public record Definition(String key, String label, String description, String category, String action, List<RoleScope> scopes) {}

    private static final List<RoleScope> ALL_SCOPES = List.of(RoleScope.ORGANIZATION, RoleScope.SPACE, RoleScope.PROJECT);
    private static final List<RoleScope> ORGANIZATION_AND_SPACE = List.of(RoleScope.ORGANIZATION, RoleScope.SPACE);
    private static final List<RoleScope> ORGANIZATION_ONLY = List.of(RoleScope.ORGANIZATION);

    private static Definition define(String key, String label, String description, List<RoleScope> scopes) {
        String module = key.substring(0, key.indexOf('.'));
        String verb = key.substring(key.lastIndexOf('.') + 1);
        String category = switch (module) {
            case "organization" -> "Organisation";
            case "space" -> "Espaces";
            case "employee" -> "Employés";
            case "resource" -> "Ressources";
            case "project" -> "Projets";
            case "task" -> "Tâches";
            case "sprint" -> "Sprints";
            case "gantt" -> "Planification (Gantt)";
            case "workload" -> "Charge des ressources";
            case "analytics" -> "Analyses";
            case "access" -> "Rôles et permissions";
            default -> "Autres";
        };
        String action = switch (verb) {
            case "view" -> "Consulter";
            case "create" -> "Créer";
            case "edit", "update" -> "Modifier";
            case "delete", "remove" -> "Supprimer";
            case "manage", "manage_accounts" -> "Gérer";
            default -> "Autre";
        };
        return new Definition(key, label, description, category, action, scopes);
    }

    public static final List<Definition> DEFINITIONS = List.of(
            define("organization.view", "View organization overview", "See organization level information.", ORGANIZATION_ONLY),
            define("organization.edit", "Edit organization profile", "Update organization contact and profile details.", ORGANIZATION_ONLY),
            define("analytics.view", "View analytics", "See available project, employee, resource, and budget allocation analytics.", ALL_SCOPES),
            define("space.view", "View spaces", "See spaces and their content.", ORGANIZATION_AND_SPACE),
            define("space.create", "Create spaces", "Create new spaces in the organization.", ORGANIZATION_ONLY),
            define("space.edit", "Edit spaces", "Rename spaces and update their settings.", ORGANIZATION_AND_SPACE),
            define("space.delete", "Delete spaces", "Delete spaces.", ORGANIZATION_AND_SPACE),
            define("employee.view", "View employees", "See employee records.", ORGANIZATION_AND_SPACE),
            define("employee.create", "Add employees", "Add employees to spaces.", ORGANIZATION_AND_SPACE),
            define("employee.manage_accounts", "Manage employee accounts", "Send and update employee sign-in credentials.", ORGANIZATION_AND_SPACE),
            define("resource.pool.view", "View space resource pools", "See employees, equipment, budgets, and availability in a space pool.", ORGANIZATION_AND_SPACE),
            define("resource.pool.manage", "Manage space resource pools", "Add or remove equipment and budget resources.", ORGANIZATION_AND_SPACE),
            define("project.view", "View projects", "See projects in accessible spaces.", ALL_SCOPES),
            define("project.create", "Create projects", "Create projects in accessible spaces.", ORGANIZATION_AND_SPACE),
            define("project.edit", "Edit projects", "Update projects and their planned dates.", ALL_SCOPES),
            define("project.delete", "Delete projects", "Delete projects.", ALL_SCOPES),
            define("project.resources.manage", "Manage project resources", "Assign and release project resources.", ALL_SCOPES),
            define("task.view", "View tasks", "See tasks in accessible projects.", ALL_SCOPES),
            define("task.view.kanban", "View Kanban boards", "See tasks arranged by status in Kanban columns.", ALL_SCOPES),
            define("task.view.scrum", "View Scrum boards", "See the backlog and sprint work organized by status.", ALL_SCOPES),
            define("task.comment", "Comment on tasks", "Read and add discussion comments to tasks.", ALL_SCOPES),
            define("task.sprint.manage", "Manage sprints", "Create and plan sprints and assign backlog tasks to them.", ALL_SCOPES),
            define("task.create", "Create tasks", "Add tasks to accessible projects.", ALL_SCOPES),
            define("task.edit", "Edit tasks", "Update tasks, their status, planned and actual dates.", ALL_SCOPES),
            define("task.delete", "Delete tasks", "Delete tasks.", ALL_SCOPES),
            define("gantt.view", "View Gantt chart", "See the Gantt chart with planned vs actual dates, margins, critical path and schedule analysis.", ALL_SCOPES),
            define("gantt.dependencies.manage", "Manage task dependencies", "Create, change and remove dependencies (type and lag) between tasks.", ALL_SCOPES),
            define("gantt.schedule.manage", "Manage the schedule", "Change planned/actual dates and progress from the Gantt chart and apply automatic rescheduling.", ALL_SCOPES),
            define("workload.view", "View resource workload", "See the daily workload of employees and equipment, overloads (surcharges) and their causes.", ALL_SCOPES),
            define("workload.manage", "Redistribute workload", "Reassign work between employees, shift tasks within their margin and apply automatic leveling proposals.", ALL_SCOPES),
            define("access.manage", "Manage roles and access", "Configure roles and assign them at this scope.", ALL_SCOPES));

    public static final List<String> GANTT_KEYS = List.of("gantt.view", "gantt.dependencies.manage", "gantt.schedule.manage");

    private static final Set<String> KNOWN_KEYS = Set.copyOf(DEFINITIONS.stream().map(Definition::key).toList());

    public static boolean exists(String key) {
        return KNOWN_KEYS.contains(key);
    }

    public static boolean allowedAt(RoleScope scope, String key) {
        return DEFINITIONS.stream().anyMatch(definition -> definition.key().equals(key) && definition.scopes().contains(scope));
    }
}
