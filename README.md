# Project Management Application

A project management application designed around a common core model that can support multiple project management methodologies such as **Scrum, Kanban, and XP**.

## Core Structure

The application is organized around four main levels:

```text
Organization
    │
    └── Space
          │
          └── Project
                │
                └── Task
```

### Organization

The top-level boundary for users, permissions, and resources.

### Space

A workspace within an organization that groups related projects and resources.

### Project

The main container for planning and executing work.

A project can use one or more project management methodologies.

### Task

The atomic unit of work within a project. Tasks have a shared lifecycle and can be organized differently depending on the enabled methodology.

## Methodologies

The application provides methodology-specific capabilities on top of the common project structure.

```text
Project
 ├── Core
 │    ├── Tasks
 │    ├── Resources
 │    ├── Roles & Permissions
 │    └── Dependencies
 │
 └── Methodologies
      ├── Scrum
      │    └── Iterations, Backlog, Estimation
      │
      ├── Kanban
      │    └── Boards, Columns, WIP, Flow
      │
      └── XP
           └── Iterations, Releases, Pairing
```

Projects can enable multiple methodologies at the same time, allowing combinations such as **Scrumban** or **XP + Kanban**.

## Architecture Principle

The core project model remains independent from individual methodologies.

Methodologies extend the core rather than replacing it, allowing the same Tasks, Projects, Resources, and Permissions to be used across different workflows.

## Main Entities

```text
Organization
 └── Space
      └── Project
           ├── Tasks
           ├── Resources
           ├── Dependencies
           └── Enabled Methodologies
                ├── Scrum
                ├── Kanban
                └── XP
```

The goal is to provide a flexible project management system where different methodologies can coexist without changing the underlying project structure.

## Current implementation scope

The current application supports organization and employee accounts, scoped roles and permissions, spaces, projects, tasks, resource pools and allocations, Gantt scheduling, workload analysis, and workspace analytics. Task views include a list, Kanban board, and sprint-based Scrum planning. Scrum sprints have a goal, planned dates, a lifecycle state, and explicit task membership.

XP planning, releases, pair programming, and combining multiple project methods are not implemented yet; the methodology diagram above describes possible product direction, not available functionality.

## Deployment configuration

Local Docker Compose keeps Hibernate schema updates enabled for developer setup. For production, activate the prod Spring profile. It switches Hibernate to schema validation, requires the database schema to be provisioned before startup, enables secure HTTP-only session cookies, and suppresses framework error details. A versioned database migration pipeline should be added before production rollout.
