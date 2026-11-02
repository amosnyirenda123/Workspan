package com.workspan.workspanbackend.service;

import java.util.List;

public final class PermissionCatalog {
    private PermissionCatalog() {}
    public record Definition(String key, String label, String description) {}

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("organization.view", "View organization overview", "See organization level information."),
            new Definition("space.view", "View spaces", "See spaces and their content."),
            new Definition("space.create", "Create spaces", "Create new spaces in the organization."),
            new Definition("space.edit", "Edit spaces", "Rename spaces and update their settings."),
            new Definition("space.delete", "Delete spaces", "Delete spaces."),
            new Definition("employee.view", "View employees", "See employee records."),
            new Definition("employee.create", "Manage employees", "Add employees to spaces."),
            new Definition("employee.manage_accounts", "Manage employee accounts", "Send and update employee sign-in credentials."),
            new Definition("resource.pool.manage", "Manage space resource pools", "Add or remove equipment and budget resources."),
            new Definition("project.view", "View projects", "See projects in accessible spaces."),
            new Definition("project.create", "Create projects", "Create projects in accessible spaces."),
            new Definition("project.edit", "Edit projects", "Update projects and their dates."),
            new Definition("project.delete", "Delete projects", "Delete projects."),
            new Definition("project.resources.manage", "Manage project resources", "Assign and release project resources."),
            new Definition("task.view", "View tasks", "See tasks in accessible projects."),
            new Definition("task.create", "Create tasks", "Add tasks to accessible projects."),
            new Definition("task.edit", "Edit tasks", "Update tasks and their status."),
            new Definition("task.delete", "Delete tasks", "Delete tasks."),
            new Definition("access.manage", "Manage roles and access", "Configure roles and assign them at this scope."));
}
