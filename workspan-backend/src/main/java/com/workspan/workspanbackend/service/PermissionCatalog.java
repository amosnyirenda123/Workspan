package com.workspan.workspanbackend.service;

import java.util.List;

public final class PermissionCatalog {
    private PermissionCatalog() {}
    public record Definition(String key, String label, String description, String category, String action) {}

    private static Definition define(String key, String label, String description) {
        String module = key.substring(0, key.indexOf('.'));
        String verb = key.substring(key.lastIndexOf('.') + 1);
        String category = switch (module) {
            case "organization" -> "Organisation";
            case "space" -> "Espaces";
            case "employee" -> "Employés";
            case "resource" -> "Ressources";
            case "project" -> "Projets";
            case "task" -> "Tâches";
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
        return new Definition(key, label, description, category, action);
    }

    public static final List<Definition> DEFINITIONS = List.of(
            define("organization.view", "View organization overview", "See organization level information."),
            define("analytics.view", "View analytics", "See available project, employee, resource, and budget allocation analytics."),
            define("space.view", "View spaces", "See spaces and their content."),
            define("space.create", "Create spaces", "Create new spaces in the organization."),
            define("space.edit", "Edit spaces", "Rename spaces and update their settings."),
            define("space.delete", "Delete spaces", "Delete spaces."),
            define("employee.view", "View employees", "See employee records."),
            define("employee.create", "Add employees", "Add employees to spaces."),
            define("employee.manage_accounts", "Manage employee accounts", "Send and update employee sign-in credentials."),
            define("resource.pool.view", "View space resource pools", "See employees, equipment, budgets, and availability in a space pool."),
            define("resource.pool.manage", "Manage space resource pools", "Add or remove equipment and budget resources."),
            define("project.view", "View projects", "See projects in accessible spaces."),
            define("project.create", "Create projects", "Create projects in accessible spaces."),
            define("project.edit", "Edit projects", "Update projects and their dates."),
            define("project.delete", "Delete projects", "Delete projects."),
            define("project.resources.manage", "Manage project resources", "Assign and release project resources."),
            define("task.view", "View tasks", "See tasks in accessible projects."),
            define("task.create", "Create tasks", "Add tasks to accessible projects."),
            define("task.edit", "Edit tasks", "Update tasks and their status."),
            define("task.delete", "Delete tasks", "Delete tasks."),
            define("access.manage", "Manage roles and access", "Configure roles and assign them at this scope."));
}
