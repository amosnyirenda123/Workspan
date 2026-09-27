# Project Management Application — Methodology Extension Architecture

## Purpose

The core domain model (Organization → Space → Project → Task, Resources, Roles/Permissions) is fixed and load-bearing. Scrum, Kanban, and XP were each designed as layers on top of it without altering it. This document extracts the pattern behind those three modules into an explicit architecture, so that:

- future methodologies (or customer-specific workflow variants) can be evaluated against the same rules
- the three cross-cutting questions raised while writing Kanban and XP get a single place to be resolved
- anyone implementing this does not have to reverse-engineer "why is this table shaped this way" from three separate documents

This document introduces **no new core-breaking concepts**. It only names and organizes decisions already made.

---

## The Core Contract

Everything below is off-limits to methodology modules:

1. `tasks.status` is the single source of truth for Task lifecycle. No module may introduce a second status field, and no module may transition a Task except through `task.transition_status`.
2. Resource occupancy is checked only at `STARTED`, only within a Space, and only as a binary busy/free state. No module may add its own conflict-check path.
3. Project, Space, and Organization isolation are absolute. No module table may allow a Task, Dependency, or Resource to reference an entity outside its own Project/Space/Organization.
4. Authorization is Permission-based, never Role-name-based. Every module adds Permissions, never authorization logic that branches on a Role's name.
5. Baseline vs. actual dates, and the re-baseline operation, are not reinterpreted by any module.

A methodology module that cannot be built while honoring all five is not a valid module under this architecture — it's a core change, and should be raised as one explicitly rather than smuggled in.

---

## Classifying Every Table a Module Adds

Every table introduced by a module falls into exactly one of five categories. This taxonomy is the reusable artifact — apply it to the next methodology, not the specific tables.

| # | Category | Description | Examples | Rule |
|---|---|---|---|---|
| 1 | **Isolated Entity** | New first-class thing, scoped to a Project (or Space), with its own lifecycle | `iterations`, `boards`, `releases` | Always carries `project_id`/`space_id` FK, fixed at creation; own status lifecycle if needed — never reuses `tasks.status` |
| 2 | **Assignment / Join Table** | Many-to-many or time-bounded relationship between an Isolated Entity and a core entity | `task_iteration_assignment`, `task_board_position`, `iteration_release_assignment` | Carries `assigned_at`/`removed_at` (or `entered_at`/`exited_at`) rather than being overwritten in place, so history is never lost |
| 3 | **Additive Extension Table** | 1:1 (or 1:0/1) table hanging off an existing core entity, adding methodology-specific attributes | `task_estimation`, `task_class_of_service`, `task_pairing` | Base table (`tasks`, `resources`) never gains a nullable methodology-specific column. Zero schema cost to Projects that don't opt in |
| 4 | **Trigger / Orchestration Layer** | A module action that calls into an existing core operation rather than duplicating its logic | Kanban's mapped columns calling `task.transition_status` | May gate an action with its own Permission, but must never reimplement or bypass the core operation |
| 5 | **Derived / Computed Metric** | Not stored — computed from core data plus module tables at query time | velocity, burndown, cycle time, cumulative flow, pair rotation frequency | If a metric needs history that doesn't exist yet, the fix is a new *history* table under category 2, not a cached metric that can drift |

**The test for any new table:** which of the five is this, and does it violate that category's rule? If a proposed table doesn't cleanly fit one of the five, that's a signal to slow down — it usually means the table is trying to touch the core.

---

## Permissions and Roles

- **New Permissions, never new Role scopes.** Org/Space/Project remain the only three scopes. A module adds `module.action` Permission keys (`sprint.manage`, `board.manage`, `release.manage`, ...) that stack through the existing union exactly like core Permissions.
- **Default Roles are templates, not logic.** Product Owner, Scrum Master, Board Manager, Coach, On-Site Customer are seed data — named bundles an Organization can rename or edit freely. Nothing in authorization code ever checks a Role's name; only `hasPermission(key)` is checked.

> An Organization could build its own bespoke methodology entirely out of existing module Permissions plus custom Roles, without needing a fourth "module" at all — a cheaper alternative to building a full new module for narrow customer requests.

---

## Workflow Configuration: From Enum to Module Set

`project_workflow_config.workflow_type` was originally a single exclusive enum (`basic | scrum | kanban | xp`). Real teams combine methodologies (Scrumban, XP iterations on a Kanban board), which a single enum can't represent.

**Resolution:** replace the enum with a set of enabled modules.

```text
project_workflow_config
 ├── project_id         UUID / PK, FK
 ├── configured_at
 └── ...

project_enabled_modules
 ├── project_id         UUID / FK
 ├── module              ENUM: scrum, kanban, xp
 ├── enabled_at
 └── ...
```

- A Project with no rows in `project_enabled_modules` is "Basic" — only core entities are active.
- A Project can enable more than one module (Scrumban = `scrum` + `kanban`; both `iterations` and `boards` become active and can reference the same Tasks).
- Each module's tables remain fully gated by whether that module is enabled for the Project.
- UI terminology ("Sprint" vs. "Iteration") is a per-module display concern, not a schema concern.

---

## Resolved Cross-Cutting Questions

### 1. `sprints` → `iterations`
Renamed. Used identically by `scrum` and `xp` — same fields, same lifecycle, different conventional duration. Only the label differs (module-display concern). `sprint.manage`-style Permissions become `iteration.manage`, shared by both modules.

### 2. Story/Task hierarchy
**Recommendation: adopt a `user_stories` entity**, Project-scoped, sitting above Task, as a **shared, methodology-neutral addition** (not scoped to XP or Scrum specifically):

- Both Scrum and XP want it; treating it as core-adjacent avoids defining it twice.
- A self-referential Task parent link would blur the Task entity's role as the atomic unit of execution and occupancy. A Story is a planning/estimation grouping, kept as a separate entity — the same way `boards` sits above `board_columns` without becoming a Task itself.
- Costs nothing to Projects that don't use it (same "empty table" principle as every Additive Extension Table).

If adopted, `user_stories` and `task_story_assignment` move into a shared "Planning" module document (title TBD), enabled independently of `scrum`/`kanban`/`xp` flags, since Kanban teams routinely want Story-level grouping too.

### 3. Coexistence (Scrumban, XP+Kanban)
Resolved by the module-set redesign above — no further schema work needed.

---

## Checklist for Evaluating a Future Methodology

Before adding a table for any new methodology:

1. Does it violate any rule in the Core Contract? If yes, stop — this is a core change and needs to be raised as one.
2. Which of the five table categories does it fall into? If none cleanly fit, slow down and re-examine whether it's actually trying to touch the core.
3. Does it need a new Permission, or does an existing one already cover it?
4. Does it need a new Role, or can it be expressed as a Role *template* using existing/new Permissions? New Role scopes are never valid; new Role templates are just seed data.
5. Is the module gated through `project_enabled_modules`, and does it degrade to zero cost (no rows, no UI) for Projects that don't enable it?
6. If it needs historical data for a metric, is that captured as its own Assignment/History table (category 2) rather than invented after the fact as a derived field that can't actually be derived?

---

## Entity Summary

| Table | Category | Module | Notes |
|---|---|---|---|
| `iterations` (was `sprints`) | Isolated Entity | scrum, xp (shared) | renamed per resolution above |
| `task_iteration_assignment` (was `task_sprint_assignment`) | Assignment | scrum, xp | rename follows table above |
| `task_backlog_rank` | Additive Extension | scrum | rank only; membership derived |
| `task_estimation` | Additive Extension | scrum, xp | empty for non-estimating Projects |
| `task_status_transitions` | History (Assignment-family) | core-adjacent | useful beyond any one module |
| `boards` | Isolated Entity | kanban | |
| `board_columns` | Isolated Entity (child) | kanban | `maps_to_status` = Trigger layer |
| `task_board_position` | Assignment | kanban | current position only |
| `task_board_position_history` | History (Assignment-family) | kanban | needed for cycle time |
| `task_class_of_service` | Additive Extension | kanban | optional |
| `releases` | Isolated Entity | xp | |
| `iteration_release_assignment` | Assignment | xp | cardinality still open |
| `task_pairing` | Additive Extension (historical) | xp | driver/navigator over time |
| `user_stories` (proposed) | Isolated Entity | shared/core-adjacent | recommended, not yet built |
| `task_story_assignment` (proposed) | Assignment | shared/core-adjacent | recommended, not yet built |
| `project_enabled_modules` | Config | core-adjacent | replaces single-enum `workflow_type` |

---

## Remaining Open Items (deferred, not blocking)

- WIP limit enforcement: hard block vs. soft warning — a values decision, not an architecture question.
- Multiple Boards per Project and whether Task membership across Boards is exclusive.
- Release–Iteration cardinality: one Release per Iteration vs. many.
- Story status derivation: computed from child Tasks vs. independently tracked.
- CI/build status integration — explicitly out of scope for this schema.

None of these block implementation of the architecture itself — they're refinements within categories already defined above.