package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ApiException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.GanttResponse;
import com.workspan.workspanbackend.dto.GanttResponse.Bar;
import com.workspan.workspanbackend.dto.GanttResponse.Link;
import com.workspan.workspanbackend.dto.GanttResponse.Summary;
import com.workspan.workspanbackend.dto.GanttResponse.TaskRow;
import com.workspan.workspanbackend.dto.GanttResponse.Timeline;
import com.workspan.workspanbackend.dto.RescheduleResponse;
import com.workspan.workspanbackend.entity.DependencyType;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskDependency;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskDependencyRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Schedule engine behind the Gantt chart (Critical Path Method on calendar days).
 *
 * <p>Internally each task occupies the half-open day interval [start, finish) expressed in epoch days, so that a
 * task planned from the 1st to the 3rd (inclusive) has start = 1, finish = 4 and a duration of 3 days.
 * Dependency rules with a lag L (negative = lead):
 * <ul>
 *     <li>FINISH_TO_START: successor.start  &ge; predecessor.finish + L</li>
 *     <li>START_TO_START:  successor.start  &ge; predecessor.start  + L</li>
 *     <li>FINISH_TO_FINISH: successor.finish &ge; predecessor.finish + L</li>
 *     <li>START_TO_FINISH: successor.finish &ge; predecessor.start  + L</li>
 * </ul>
 *
 * <p>Forecast (forward pass): completed tasks are fixed on their actual dates; started tasks keep their actual start
 * and finish after their remaining work (duration x (100 - progress)%) counted from today; tasks that have not started
 * start no earlier than their planned start, today, and every predecessor constraint.
 * The backward pass from the forecast project finish gives late dates, total float (margin before delaying the
 * project) and free float (margin before delaying a successor). Tasks with no total float are critical.
 */
@Service
@Transactional(readOnly = true)
public class GanttService {
    private static final int PROGRESS_TOLERANCE = 10;
    private static final int TIMELINE_PADDING_DAYS = 3;

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final TaskService taskService;

    public GanttService(ProjectRepository projects, TaskRepository tasks, TaskDependencyRepository dependencies, TaskService taskService) {
        this.projects = projects; this.tasks = tasks; this.dependencies = dependencies; this.taskService = taskService;
    }

    public GanttResponse gantt(UUID projectId, UUID organizationId) {
        Project project = requireProject(projectId, organizationId);
        return compute(project, LocalDate.now());
    }

    /**
     * Moves the planned dates of tasks that have not started yet to their forecast dates, so the plan satisfies every
     * dependency (and does not start in the past). Started and completed tasks are never moved. All-or-nothing.
     */
    @Transactional
    public RescheduleResponse reschedule(UUID projectId, UUID organizationId) {
        Project project = requireProject(projectId, organizationId);
        GanttResponse gantt = compute(project, LocalDate.now());
        Map<UUID, Task> byId = new HashMap<>();
        tasks.findAllByProjectIdOrderByNameAsc(projectId).forEach(task -> byId.put(task.getId(), task));
        List<RescheduleResponse.Move> moves = new ArrayList<>();
        List<TaskRow> ordered = gantt.tasks().stream()
                .filter(row -> row.scheduled() && row.forecastStart() != null)
                .sorted(Comparator.comparing(TaskRow::forecastStart)).toList();
        for (TaskRow row : ordered) {
            Task task = byId.get(row.id());
            if (task == null || task.getStatus() != TaskStatus.TODO || task.getStartDate() != null) continue;
            LocalDate plannedStart = task.getBaselineStart();
            if (plannedStart != null && !row.forecastStart().isAfter(plannedStart)) continue;
            LocalDate previousStart = task.getBaselineStart(), previousEnd = task.getBaselineEnd();
            try {
                taskService.applySchedule(task, row.forecastStart(), row.forecastEnd(), null, null, task.getProgress());
            } catch (ApiException cause) {
                Map<String, Object> params = new java.util.LinkedHashMap<>(cause.getParams());
                params.put("task", task.getName());
                params.put("reason", cause.getMessage());
                params.put("causeCode", cause.getCode().name());
                throw new ConflictException(ErrorCode.TASK_RESCHEDULE_BLOCKED, params);
            }
            moves.add(new RescheduleResponse.Move(task.getId(), task.getName(), previousStart, previousEnd, row.forecastStart(), row.forecastEnd()));
        }
        return new RescheduleResponse(moves.size(), moves);
    }

    // ------------------------------------------------------------------ engine

    /** Computes the schedule of a project as of the given day (used by the Gantt chart and the workload analysis). */
    public GanttResponse compute(Project project, LocalDate todayDate) {
        long today = todayDate.toEpochDay();
        List<Task> projectTasks = tasks.findAllByProjectIdOrderByNameAsc(project.getId());
        List<TaskDependency> links = dependencies.findAllByPredecessorTaskProjectId(project.getId());

        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Task task : projectTasks) nodes.put(task.getId(), new Node(task, today));

        // Keep only links between scheduled tasks of this project; others are reported as warnings.
        List<TaskDependency> usable = new ArrayList<>();
        for (TaskDependency link : links) {
            Node pred = nodes.get(link.getPredecessorTask().getId());
            Node succ = nodes.get(link.getSuccessorTask().getId());
            if (pred == null || succ == null) continue;
            if (!pred.scheduled || !succ.scheduled) {
                (pred.scheduled ? succ : pred).warnings.add("Dépendance ignorée : une des tâches n'a pas de dates planifiées");
                continue;
            }
            usable.add(link);
            pred.successors.add(link);
            succ.predecessors.add(link);
        }

        List<Node> order = topologicalOrder(nodes);

        // Forward pass (forecast / early dates).
        for (Node node : order) {
            if (!node.scheduled) continue;
            if (node.fixed) { node.es = node.fixedStart; node.ef = node.fixedFinish; continue; }
            if (node.started) {
                long remaining = (long) Math.ceil(node.duration * (100 - node.task.getProgress()) / 100.0);
                long finish = Math.max(node.actualStart + node.duration, today + Math.max(1, remaining));
                for (TaskDependency link : node.predecessors) finish = Math.max(finish, requiredFinish(link, nodes, node.duration));
                node.es = node.actualStart;
                node.ef = finish;
                continue;
            }
            long start = Math.max(node.baseStart, today);
            for (TaskDependency link : node.predecessors) start = Math.max(start, requiredStart(link, nodes, node.duration));
            node.es = start;
            node.ef = start + node.duration;
        }

        List<Node> scheduled = order.stream().filter(node -> node.scheduled).toList();
        Long projectFinish = scheduled.stream().map(node -> node.ef).max(Long::compare).orElse(null);

        // Backward pass (late dates) from the forecast project finish.
        List<Node> reversed = new ArrayList<>(scheduled);
        java.util.Collections.reverse(reversed);
        for (Node node : reversed) {
            long lf = projectFinish;
            for (TaskDependency link : node.successors) {
                Node succ = nodes.get(link.getSuccessorTask().getId());
                int lag = lag(link);
                long bound = switch (link.getType()) {
                    case FINISH_TO_START -> succ.ls - lag;
                    case START_TO_START -> succ.ls - lag + node.duration();
                    case FINISH_TO_FINISH -> succ.lf - lag;
                    case START_TO_FINISH -> succ.lf - lag + node.duration();
                };
                lf = Math.min(lf, bound);
            }
            node.lf = lf;
            node.ls = lf - node.duration();
        }

        // Floats.
        for (Node node : scheduled) {
            node.totalFloat = node.lf - node.ef;
            long free = projectFinish - node.ef;
            for (TaskDependency link : node.successors) free = Math.min(free, linkSlack(link, nodes));
            node.freeFloat = Math.max(0, free);
            node.critical = !node.fixed && node.totalFloat <= 0;
        }

        // Links.
        List<Link> linkRows = new ArrayList<>();
        int violations = 0;
        for (TaskDependency link : links) {
            Node pred = nodes.get(link.getPredecessorTask().getId());
            Node succ = nodes.get(link.getSuccessorTask().getId());
            if (pred == null || succ == null) continue;
            boolean usableLink = usable.contains(link);
            boolean critical = usableLink && pred.critical && succ.critical && linkSlack(link, nodes) <= 0;
            String violation = usableLink ? violation(link, pred, succ) : null;
            if (violation != null) {
                violations++;
                succ.warnings.add(violation);
            }
            linkRows.add(new Link(link.getId(), pred.task.getId(), succ.task.getId(), link.getType(), lag(link), critical, violation != null, violation));
        }

        // Timeline.
        List<Long> starts = new ArrayList<>(), ends = new ArrayList<>();
        addIfPresent(starts, project.getBaselineStart());
        addIfPresent(ends, project.getBaselineEnd());
        addIfPresent(starts, project.getStartDate());
        addIfPresent(ends, project.getEndDate());
        for (Node node : nodes.values()) {
            if (node.plannedStart != null) starts.add(node.plannedStart);
            if (node.plannedEnd != null) ends.add(node.plannedEnd);
            if (node.actualStart != null) starts.add(node.actualStart);
            if (node.actualEnd != null) ends.add(node.actualEnd);
            if (node.scheduled) { starts.add(node.es); ends.add(node.ef - 1); }
        }
        long timelineStart = (starts.isEmpty() ? today : Math.min(starts.stream().min(Long::compare).get(), today)) - TIMELINE_PADDING_DAYS;
        long timelineEnd = (ends.isEmpty() ? today : Math.max(ends.stream().max(Long::compare).get(), today)) + TIMELINE_PADDING_DAYS;
        Timeline timeline = new Timeline(date(timelineStart), date(timelineEnd), todayDate, timelineEnd - timelineStart + 1);

        // Task rows.
        List<TaskRow> rows = new ArrayList<>();
        int late = 0, completed = 0, critical = 0, unscheduled = 0;
        long weightTotal = 0, weightedProgress = 0, weightedExpected = 0;
        for (Node node : nodes.values()) {
            Task task = node.task;
            int expected = node.expectedProgress(today);
            Long startVariance = null, finishVariance = null;
            if (node.plannedStart != null && node.scheduled) startVariance = (node.actualStart != null ? node.actualStart : node.es) - node.plannedStart;
            if (node.plannedEnd != null && node.scheduled) finishVariance = (node.actualEnd != null ? node.actualEnd : node.ef - 1) - node.plannedEnd;
            boolean isLate = task.getStatus() != TaskStatus.DONE && node.scheduled && (
                    finishVariance != null && finishVariance > 0
                    || node.plannedEnd != null && today > node.plannedEnd
                    || node.actualStart == null && node.plannedStart != null && today > node.plannedStart);
            String state = state(node, isLate, expected, finishVariance);
            if (isLate) { late++; node.warnings.add(finishVariance != null && finishVariance > 0
                    ? "Fin prévue avec " + finishVariance + " jour(s) de retard sur la planification"
                    : "La tâche aurait déjà dû démarrer"); }
            if (task.getStatus() == TaskStatus.DONE) completed++;
            if (node.critical) critical++;
            if (!node.scheduled) { unscheduled++; node.warnings.add("Aucune date planifiée"); }
            if (task.getStatus() == TaskStatus.BLOCKED) node.warnings.add("Tâche bloquée");

            long weight = node.scheduled ? node.duration : 1;
            weightTotal += weight;
            weightedProgress += weight * task.getProgress();
            weightedExpected += weight * expected;

            Bar plannedBar = node.plannedStart != null && node.plannedEnd != null
                    ? new Bar(node.plannedStart - timelineStart, node.plannedEnd - node.plannedStart + 1) : null;
            Bar forecastBar = node.scheduled ? new Bar(node.es - timelineStart, node.ef - node.es) : null;
            Bar actualBar = node.actualStart != null
                    ? new Bar(node.actualStart - timelineStart, (node.actualEnd != null ? node.actualEnd : Math.max(today, node.actualStart)) - node.actualStart + 1) : null;

            rows.add(new TaskRow(task.getId(), task.getName(), task.getStatus(), task.getProgress(),
                    date(node.plannedStart), date(node.plannedEnd),
                    node.plannedStart != null && node.plannedEnd != null ? node.plannedEnd - node.plannedStart + 1 : null,
                    task.getStartDate(), task.getEndDate(),
                    node.scheduled ? date(node.es) : null, node.scheduled ? date(node.ef - 1) : null,
                    node.scheduled ? date(node.es) : null, node.scheduled ? date(node.ef - 1) : null,
                    node.scheduled ? date(node.ls) : null, node.scheduled ? date(node.lf - 1) : null,
                    node.scheduled && !node.fixed ? node.totalFloat : null,
                    node.scheduled && !node.fixed ? node.freeFloat : null,
                    node.critical, startVariance, finishVariance, expected, state, isLate, node.scheduled,
                    plannedBar, forecastBar, actualBar, List.copyOf(node.warnings)));
        }
        rows.sort(Comparator.comparing((TaskRow row) -> row.forecastStart() == null ? LocalDate.MAX : row.forecastStart())
                .thenComparing(row -> row.plannedStart() == null ? LocalDate.MAX : row.plannedStart())
                .thenComparing(TaskRow::name));

        List<UUID> criticalPath = scheduled.stream().filter(node -> node.critical)
                .sorted(Comparator.comparingLong((Node node) -> node.es).thenComparingLong(node -> node.ef))
                .map(node -> node.task.getId()).toList();

        // Project summary.
        LocalDate plannedStart = project.getBaselineStart() != null ? project.getBaselineStart()
                : nodes.values().stream().map(node -> node.plannedStart).filter(Objects::nonNull).min(Long::compare).map(GanttService::date).orElse(null);
        LocalDate plannedEnd = project.getBaselineEnd() != null ? project.getBaselineEnd()
                : nodes.values().stream().map(node -> node.plannedEnd).filter(Objects::nonNull).max(Long::compare).map(GanttService::date).orElse(null);
        LocalDate forecastEnd = project.getEndDate() != null ? project.getEndDate() : projectFinish == null ? null : date(projectFinish - 1);
        Long finishVariance = plannedEnd != null && forecastEnd != null ? forecastEnd.toEpochDay() - plannedEnd.toEpochDay() : null;
        Long margin = finishVariance == null ? null : -finishVariance;
        Long plannedDuration = plannedStart != null && plannedEnd != null ? plannedEnd.toEpochDay() - plannedStart.toEpochDay() + 1 : null;
        int progress = weightTotal == 0 ? 0 : (int) Math.round((double) weightedProgress / weightTotal);
        int expected = weightTotal == 0 ? 0 : (int) Math.round((double) weightedExpected / weightTotal);
        Double spi = expected == 0 ? null : Math.round(progress * 100.0 / expected) / 100.0;
        String health;
        if (nodes.isEmpty()) health = "NO_TASKS";
        else if (project.getEndDate() != null) health = finishVariance != null && finishVariance > 0 ? "COMPLETED_LATE" : "COMPLETED";
        else if (finishVariance != null && finishVariance > 0) health = "LATE";
        else if (late > 0 || violations > 0 || spi != null && spi < 0.9) health = "AT_RISK";
        else health = "ON_TRACK";

        Summary summary = new Summary(project.getId(), project.getName(), plannedStart, plannedEnd,
                project.getStartDate(), project.getEndDate(), forecastEnd, finishVariance, margin, plannedDuration,
                progress, expected, spi, health, nodes.size(), completed, late, critical, unscheduled, violations);
        return new GanttResponse(summary, timeline, rows, linkRows, criticalPath);
    }

    private static String state(Node node, boolean late, int expected, Long finishVariance) {
        Task task = node.task;
        if (task.getStatus() == TaskStatus.DONE) return finishVariance != null && finishVariance > 0 ? "COMPLETED_LATE" : "COMPLETED_ON_TIME";
        if (!node.scheduled) return "UNSCHEDULED";
        if (task.getStatus() == TaskStatus.BLOCKED) return "BLOCKED";
        if (late) return "LATE";
        if (node.actualStart == null) return "NOT_STARTED";
        if (task.getProgress() + PROGRESS_TOLERANCE < expected) return "BEHIND";
        if (task.getProgress() > expected + PROGRESS_TOLERANCE) return "AHEAD";
        return "ON_TRACK";
    }

    /** Checks the plan (planned dates) and the execution (actual dates) against a dependency. */
    private static String violation(TaskDependency link, Node pred, Node succ) {
        int lag = lag(link);
        if (succ.started || succ.fixed) {
            long succStart = succ.actualStart;
            if (link.getType() == DependencyType.FINISH_TO_START) {
                if (pred.actualEnd == null) return "Démarrée avant la fin de « " + pred.task.getName() + " »";
                if (succStart < pred.actualEnd + 1 + lag) return "Démarrée trop tôt par rapport à « " + pred.task.getName() + " » (FS, décalage " + lag + " j)";
            }
            if (link.getType() == DependencyType.START_TO_START) {
                if (pred.actualStart == null) return "Démarrée avant le début de « " + pred.task.getName() + " »";
                if (succStart < pred.actualStart + lag) return "Démarrée trop tôt par rapport à « " + pred.task.getName() + " » (SS, décalage " + lag + " j)";
            }
            return null;
        }
        if (pred.plannedStart == null || pred.plannedEnd == null || succ.plannedStart == null || succ.plannedEnd == null) return null;
        long ps = pred.plannedStart, pf = pred.plannedEnd + 1, ss = succ.plannedStart, sf = succ.plannedEnd + 1;
        boolean ok = switch (link.getType()) {
            case FINISH_TO_START -> ss >= pf + lag;
            case START_TO_START -> ss >= ps + lag;
            case FINISH_TO_FINISH -> sf >= pf + lag;
            case START_TO_FINISH -> sf >= ps + lag;
        };
        return ok ? null : "La planification ne respecte pas la dépendance " + label(link.getType()) + " avec « " + pred.task.getName() + " » (décalage " + lag + " j)";
    }

    private static String label(DependencyType type) {
        return switch (type) {
            case FINISH_TO_START -> "fin → début";
            case START_TO_START -> "début → début";
            case FINISH_TO_FINISH -> "fin → fin";
            case START_TO_FINISH -> "début → fin";
        };
    }

    private static long requiredStart(TaskDependency link, Map<UUID, Node> nodes, long duration) {
        Node pred = nodes.get(link.getPredecessorTask().getId());
        int lag = lag(link);
        return switch (link.getType()) {
            case FINISH_TO_START -> pred.ef + lag;
            case START_TO_START -> pred.es + lag;
            case FINISH_TO_FINISH -> pred.ef + lag - duration;
            case START_TO_FINISH -> pred.es + lag - duration;
        };
    }

    private static long requiredFinish(TaskDependency link, Map<UUID, Node> nodes, long duration) {
        return requiredStart(link, nodes, duration) + duration;
    }

    /** How many days the predecessor can slip before this link pushes the successor. */
    private static long linkSlack(TaskDependency link, Map<UUID, Node> nodes) {
        Node pred = nodes.get(link.getPredecessorTask().getId());
        Node succ = nodes.get(link.getSuccessorTask().getId());
        int lag = lag(link);
        return switch (link.getType()) {
            case FINISH_TO_START -> succ.es - lag - pred.ef;
            case START_TO_START -> succ.es - lag - pred.es;
            case FINISH_TO_FINISH -> succ.ef - lag - pred.ef;
            case START_TO_FINISH -> succ.ef - lag - pred.es;
        };
    }

    private static List<Node> topologicalOrder(Map<UUID, Node> nodes) {
        Map<UUID, Integer> inDegree = new HashMap<>();
        nodes.values().forEach(node -> inDegree.put(node.task.getId(), node.predecessors.size()));
        Comparator<Node> byStart = Comparator.comparingLong((Node node) -> node.scheduled ? node.baseStart : Long.MAX_VALUE)
                .thenComparing(node -> node.task.getName());
        List<Node> ready = new ArrayList<>(nodes.values().stream().filter(node -> inDegree.get(node.task.getId()) == 0).sorted(byStart).toList());
        Deque<Node> queue = new ArrayDeque<>(ready);
        List<Node> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            order.add(node);
            for (TaskDependency link : node.successors) {
                UUID id = link.getSuccessorTask().getId();
                int remaining = inDegree.merge(id, -1, Integer::sum);
                if (remaining == 0) queue.add(nodes.get(id));
            }
        }
        if (order.size() < nodes.size()) {
            // Defensive: cycles are rejected when dependencies are created, but never loop forever on bad data.
            for (Node node : nodes.values()) if (!order.contains(node)) {
                node.warnings.add("Dépendance circulaire détectée");
                node.predecessors.clear();
                order.add(node);
            }
        }
        return order;
    }

    private static int lag(TaskDependency link) { return link.getLag() == null ? 0 : link.getLag(); }
    private static LocalDate date(Long epochDay) { return epochDay == null ? null : LocalDate.ofEpochDay(epochDay); }
    private static void addIfPresent(List<Long> list, LocalDate date) { if (date != null) list.add(date.toEpochDay()); }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }

    /** Working state of one task during the calculation (epoch days, finish exclusive). */
    private static final class Node {
        final Task task;
        final Long plannedStart, plannedEnd, actualStart, actualEnd;
        final boolean scheduled, started, fixed;
        final long duration, baseStart;
        long fixedStart, fixedFinish;
        long es, ef, ls, lf, totalFloat, freeFloat;
        boolean critical;
        final List<TaskDependency> predecessors = new ArrayList<>();
        final List<TaskDependency> successors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        Node(Task task, long today) {
            this.task = task;
            plannedStart = epoch(task.getBaselineStart());
            plannedEnd = epoch(task.getBaselineEnd());
            actualStart = epoch(task.getStartDate());
            actualEnd = epoch(task.getEndDate());
            Long ps = plannedStart != null ? plannedStart : plannedEnd;
            Long pe = plannedEnd != null ? plannedEnd : plannedStart;
            long d;
            if (ps != null) d = Math.max(1, pe - ps + 1);
            else if (actualStart != null) d = Math.max(1, (actualEnd != null ? actualEnd : Math.max(today, actualStart)) - actualStart + 1);
            else d = 1;
            duration = d;
            scheduled = ps != null || actualStart != null;
            baseStart = ps != null ? ps : actualStart != null ? actualStart : today;
            started = actualStart != null && task.getStatus() != TaskStatus.DONE;
            fixed = task.getStatus() == TaskStatus.DONE && scheduled;
            if (fixed) {
                fixedStart = actualStart != null ? actualStart : baseStart;
                long end = actualEnd != null ? actualEnd : (pe != null ? pe : fixedStart);
                fixedFinish = Math.max(fixedStart, end) + 1;
            }
        }

        long duration() { return fixed ? fixedFinish - fixedStart : duration; }

        int expectedProgress(long today) {
            if (plannedStart == null || plannedEnd == null) return task.getStatus() == TaskStatus.DONE ? 100 : 0;
            if (today < plannedStart) return 0;
            if (today > plannedEnd) return 100;
            return (int) Math.round((today - plannedStart + 1) * 100.0 / (plannedEnd - plannedStart + 1));
        }

        private static Long epoch(LocalDate date) { return date == null ? null : date.toEpochDay(); }
    }
}
