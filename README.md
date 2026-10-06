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
