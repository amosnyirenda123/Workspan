package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ConflictException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.NotFoundException;
import com.workspan.workspanbackend.dto.GanttResponse;
import com.workspan.workspanbackend.dto.GanttResponse.TaskRow;
import com.workspan.workspanbackend.dto.LevelingProposal;
import com.workspan.workspanbackend.dto.WorkloadRequests;
import com.workspan.workspanbackend.dto.WorkloadResponse;
import com.workspan.workspanbackend.entity.EquipmentResource;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.ProjectResourceAllocation;
import com.workspan.workspanbackend.entity.Resource;
import com.workspan.workspanbackend.entity.ResourceType;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskResourceAllocation;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.ResourceRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workload analysis (charge / surcharge) and redistribution.
 *
 * <p>Each task assignment occupies its resource over the task's <b>forecast</b> window from the Gantt engine (actual dates
 * when known, otherwise planned dates pushed by dependencies and delays), so a task running late shows up as overload
 * on the following work of the same person. Employees: an assignment uses a percentage of one person's day and the
 * capacity is 100%. Equipment: an assignment uses units and the capacity is the number of units owned. Load is summed
 * across every project of the space. Days are calendar days.
 *
 * <p>Redistribution: reassign an employee's assignment to a colleague with spare capacity, shift a task that has not
 * started within its margin, or compute an automatic leveling proposal (greedy: for the earliest overloaded day, first
 * try to delay a contributing task within its free margin — so no successor and no project end moves — then try to
 * reassign an employee assignment to the least loaded colleague who stays at or under 100%).
 */
@Service
@Transactional(readOnly = true)
public class WorkloadService {
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_LEVELING_STEPS = 500;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectRepository projects;
    private final ResourceRepository resources;
    private final TaskRepository tasks;
    private final TaskResourceAllocationRepository taskAllocations;
    private final ProjectResourceAllocationRepository projectAllocations;
    private final GanttService gantt;
    private final TaskService taskService;

    public WorkloadService(ProjectRepository projects, ResourceRepository resources, TaskRepository tasks,
                           TaskResourceAllocationRepository taskAllocations, ProjectResourceAllocationRepository projectAllocations,
                           GanttService gantt, TaskService taskService) {
        this.projects = projects; this.resources = resources; this.tasks = tasks; this.taskAllocations = taskAllocations;
        this.projectAllocations = projectAllocations; this.gantt = gantt; this.taskService = taskService;
    }

    // ================================================================== analysis

    /** Resources of a project's pool (human and equipment), used for the project-scoped view. */
    public Set<UUID> projectPoolResourceIds(UUID projectId) {
        Set<UUID> ids = new HashSet<>();
        for (ProjectResourceAllocation allocation : projectAllocations.findAllByProjectIdOrderByResourceTypeAscResourceNameAsc(projectId)) {
            Resource resource = real(allocation.getResource());
            if (resource instanceof HumanResource || resource instanceof EquipmentResource) ids.add(resource.getId());
        }
        return ids;
    }

    public UUID spaceOfProject(UUID projectId, UUID organizationId) {
        return requireProject(projectId, organizationId).getSpace().getId();
    }

    /**
     * @param resourceFilter   resources to report (null = every human/equipment resource of the space)
     * @param visibleProjects  projects whose task names the viewer may see; other assignments are anonymised
     */
    public WorkloadResponse analyse(UUID spaceId, LocalDate from, LocalDate to, Set<UUID> resourceFilter, Set<UUID> visibleProjects) {
        LocalDate today = LocalDate.now();
        Range range = range(from, to, today);
        Context context = context(spaceId, today);

        List<WorkloadResponse.ResourceLoad> rows = new ArrayList<>();
        int overloaded = 0, overloadDays = 0, peak = 0, unscheduled = 0;
        long utilisationSum = 0, utilisationCount = 0;
        for (Resource resource : context.resources.values()) {
            if (resourceFilter != null && !resourceFilter.contains(resource.getId())) continue;
            int capacity = capacity(resource);
            int[] load = new int[range.days];
            List<Item> items = context.itemsOf(resource.getId());
            for (Item item : items) {
                if (item.start == null) { unscheduled++; continue; }
                for (long day = Math.max(item.start, range.start); day <= Math.min(item.end, range.end); day++) load[(int) (day - range.start)] += item.amount;
            }
            List<Integer> loadList = new ArrayList<>(), utilisation = new ArrayList<>();
            int peakLoad = 0, peakUtilisation = 0, resourceOverloadDays = 0;
            long sum = 0;
            for (int i = 0; i < range.days; i++) {
                int u = utilisation(load[i], capacity);
                loadList.add(load[i]); utilisation.add(u);
                peakLoad = Math.max(peakLoad, load[i]); peakUtilisation = Math.max(peakUtilisation, u);
                sum += u;
                if (load[i] > capacity && range.start + i >= today.toEpochDay()) resourceOverloadDays++;
            }
            List<WorkloadResponse.Period> periods = periods(load, capacity, range, items);
            Set<UUID> overloadedTasks = new HashSet<>();
            periods.forEach(period -> overloadedTasks.addAll(period.taskIds()));
            int average = range.days == 0 ? 0 : (int) Math.round((double) sum / range.days);
            utilisationSum += sum; utilisationCount += range.days;
            if (resourceOverloadDays > 0) overloaded++;
            overloadDays += resourceOverloadDays;
            peak = Math.max(peak, peakUtilisation);

            List<WorkloadResponse.Assignment> assignments = items.stream()
                    .sorted(Comparator.comparing((Item item) -> item.start == null ? Long.MAX_VALUE : item.start).thenComparing(item -> item.task.getName()))
                    .map(item -> assignment(item, visibleProjects, overloadedTasks.contains(item.task.getId()))).toList();
            String jobTitle = resource instanceof HumanResource human ? human.getJobTitle() : null;
            rows.add(new WorkloadResponse.ResourceLoad(resource.getId(), resource.getName(), resource.getType(), jobTitle, capacity,
                    resource instanceof HumanResource ? "PERCENT" : "UNITS", loadList, utilisation, peakLoad, peakUtilisation, average,
                    resourceOverloadDays, periods, assignments));
        }
        rows.sort(Comparator.comparing((WorkloadResponse.ResourceLoad row) -> row.type() != ResourceType.HUMAN)
                .thenComparing(row -> -row.overloadDays()).thenComparing(WorkloadResponse.ResourceLoad::name));
        int average = utilisationCount == 0 ? 0 : (int) Math.round((double) utilisationSum / utilisationCount);
        return new WorkloadResponse(date(range.start), date(range.end), today, range.days,
                new WorkloadResponse.Summary(rows.size(), overloaded, overloadDays, peak, average, unscheduled), rows);
    }

    private WorkloadResponse.Assignment assignment(Item item, Set<UUID> visibleProjects, boolean overloaded) {
        boolean visible = visibleProjects.contains(item.project.getId());
        TaskRow row = item.row;
        Long totalFloat = row == null ? null : row.totalFloatDays();
        boolean shiftable = visible && isNotStarted(item.task) && row != null && row.scheduled() && totalFloat != null && totalFloat > 0;
        return new WorkloadResponse.Assignment(visible ? item.task.getId() : null, visible ? item.task.getName() : "Tâche d'un autre projet",
                visible ? item.project.getId() : null, visible ? item.project.getName() : "Autre projet", visible,
                item.task.getStatus(), date(item.start), date(item.end), item.amount,
                row != null && row.critical(), totalFloat, row == null ? null : row.freeFloatDays(), shiftable, overloaded, item.start != null);
    }

    private static List<WorkloadResponse.Period> periods(int[] load, int capacity, Range range, List<Item> items) {
        List<WorkloadResponse.Period> periods = new ArrayList<>();
        int i = 0;
        while (i < load.length) {
            if (load[i] <= capacity) { i++; continue; }
            int startIndex = i, peakLoad = 0;
            while (i < load.length && load[i] > capacity) { peakLoad = Math.max(peakLoad, load[i]); i++; }
            long start = range.start + startIndex, end = range.start + i - 1;
            List<UUID> taskIds = items.stream().filter(item -> item.start != null && item.start <= end && item.end >= start)
                    .map(item -> item.task.getId()).distinct().toList();
            periods.add(new WorkloadResponse.Period(date(start), date(end), (int) (end - start + 1), peakLoad, utilisation(peakLoad, capacity), taskIds));
        }
        return periods;
    }

    // ================================================================== reassignment

    /** Employees of the space who could take over an assignment, with their resulting peak load over the task period. */
    public List<WorkloadRequests.Candidate> candidates(UUID taskId, UUID resourceId, UUID organizationId) {
        Task task = taskService.requireTask(taskId, organizationId);
        Project project = task.getProject();
        Context context = context(project.getSpace().getId(), LocalDate.now());
        Item item = context.items.stream().filter(candidate -> candidate.task.getId().equals(taskId) && candidate.resource.getId().equals(resourceId)).findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));
        if (!(item.resource instanceof HumanResource)) throw new BadRequestException(ErrorCode.ONLY_EMPLOYEES_REASSIGNABLE);
        Set<UUID> onTask = context.items.stream().filter(other -> other.task.getId().equals(taskId)).map(other -> other.resource.getId()).collect(java.util.stream.Collectors.toSet());
        Set<UUID> pool = projectPoolResourceIds(project.getId());
        List<WorkloadRequests.Candidate> result = new ArrayList<>();
        for (Resource resource : context.resources.values()) {
            if (!(resource instanceof HumanResource human) || onTask.contains(resource.getId()) || !"AVAILABLE".equalsIgnoreCase(resource.getStatus())) continue;
            int peak = 0; long sum = 0; int days = 0;
            if (item.start != null) {
                for (long day = item.start; day <= item.end; day++) {
                    int load = context.loadOn(resource.getId(), day) + item.amount;
                    peak = Math.max(peak, load); sum += load; days++;
                }
            }
            result.add(new WorkloadRequests.Candidate(resource.getId(), resource.getName(), human.getJobTitle(), pool.contains(resource.getId()),
                    peak, days == 0 ? 0 : (int) Math.round((double) sum / days), peak <= 100));
        }
        result.sort(Comparator.comparing((WorkloadRequests.Candidate candidate) -> !candidate.fits())
                .thenComparingInt(WorkloadRequests.Candidate::peakUtilisationWithTask).thenComparing(WorkloadRequests.Candidate::name));
        return result;
    }

    /** True when reassigning to this employee also adds them to the task's project pool. */
    public boolean requiresPoolAddition(UUID taskId, UUID toResourceId, UUID organizationId) {
        Task task = taskService.requireTask(taskId, organizationId);
        return projectAllocations.findByProjectIdAndResourceId(task.getProject().getId(), toResourceId).isEmpty();
    }

    public UUID projectOfTask(UUID taskId, UUID organizationId) {
        return taskService.requireTask(taskId, organizationId).getProject().getId();
    }

    /** Moves an employee assignment to another employee of the same space, keeping its charge (%). */
    @Transactional
    public void reassign(UUID organizationId, WorkloadRequests.Reassign request) {
        Task task = taskService.requireTask(request.taskId(), organizationId);
        if (task.getStatus() == TaskStatus.DONE) throw new ConflictException(ErrorCode.COMPLETED_TASK_NOT_REASSIGNABLE);
        if (request.fromResourceId().equals(request.toResourceId())) throw new BadRequestException(ErrorCode.SAME_EMPLOYEE_REASSIGNMENT);
        Project project = task.getProject();
        ProjectResourceAllocation fromPool = projectAllocations.findByProjectIdAndResourceId(project.getId(), request.fromResourceId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));
        TaskResourceAllocation assignment = taskAllocations.findByTaskIdAndProjectResourceAllocationId(task.getId(), fromPool.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));
        if (real(fromPool.getResource()).getType() != ResourceType.HUMAN) throw new BadRequestException(ErrorCode.ONLY_EMPLOYEES_REASSIGNABLE);
        Resource target = resources.findById(request.toResourceId()).map(WorkloadService::real)
                .filter(resource -> resource.getSpace().getId().equals(project.getSpace().getId()) && resource.getType() == ResourceType.HUMAN)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_IN_SPACE));
        if (!"AVAILABLE".equalsIgnoreCase(target.getStatus())) throw new ConflictException(ErrorCode.EMPLOYEE_UNAVAILABLE);
        ProjectResourceAllocation toPool = projectAllocations.findByProjectIdAndResourceId(project.getId(), target.getId()).orElseGet(() -> {
            ProjectResourceAllocation created = new ProjectResourceAllocation();
            created.setProject(project); created.setResource(target);
            return projectAllocations.saveAndFlush(created);
        });
        if (taskAllocations.findByTaskIdAndProjectResourceAllocationId(task.getId(), toPool.getId()).isPresent())
            throw new ConflictException(ErrorCode.EMPLOYEE_ALREADY_ON_TASK, Map.of("employee", target.getName()));
        assignment.setProjectResourceAllocation(toPool);
        if (assignment.getAllocationPercent() == null) assignment.setAllocationPercent(100);
        taskAllocations.save(assignment);
    }

    // ================================================================== shifting

    /** Delays a task that has not started by {@code days}, within its total margin (the project end does not move). */
    @Transactional
    public void shift(UUID organizationId, WorkloadRequests.Shift request) {
        Task task = taskService.requireTask(request.taskId(), organizationId);
        shiftTask(task, request.days(), false);
    }

    private void shiftTask(Task task, int days, boolean freeMarginOnly) {
        if (!isNotStarted(task)) throw new ConflictException(ErrorCode.TASK_ALREADY_STARTED, Map.of("task", task.getName()));
        TaskRow row = gantt.compute(task.getProject(), LocalDate.now()).tasks().stream().filter(item -> item.id().equals(task.getId())).findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.TASK_NOT_FOUND));
        if (!row.scheduled() || row.forecastStart() == null) throw new BadRequestException(ErrorCode.TASK_NOT_SCHEDULED, Map.of("task", task.getName()));
        Long margin = freeMarginOnly ? row.freeFloatDays() : row.totalFloatDays();
        if (margin == null || days > margin)
            throw new ConflictException(ErrorCode.SHIFT_EXCEEDS_MARGIN, Map.of("task", task.getName(), "days", days,
                    "marginType", freeMarginOnly ? "free" : "total", "margin", margin == null ? 0 : margin));
        taskService.applySchedule(task, row.forecastStart().plusDays(days), row.forecastEnd().plusDays(days), null, null, task.getProgress());
    }

    // ================================================================== leveling

    /**
     * Greedy leveling over the period from today (or {@code from} if later) to {@code to}.
     *
     * @param focusProjectId when set, only tasks of this project are moved/reassigned
     */
    public LevelingProposal leveling(UUID spaceId, LocalDate from, LocalDate to, UUID focusProjectId, Set<UUID> visibleProjects) {
        LocalDate today = LocalDate.now();
        Range range = range(from == null || from.isBefore(today) ? today : from, to, today);
        Context context = context(spaceId, today);

        Map<UUID, int[]> load = new HashMap<>();
        Map<UUID, Integer> capacity = new HashMap<>();
        for (Resource resource : context.resources.values()) { load.put(resource.getId(), new int[range.days]); capacity.put(resource.getId(), capacity(resource)); }
        List<Sim> sims = new ArrayList<>();
        for (Item item : context.items) {
            if (item.start == null || !load.containsKey(item.resource.getId())) continue;
            Sim sim = new Sim(item);
            sims.add(sim);
            add(load.get(sim.resourceId), sim.start(), sim.end(), item.amount, range);
        }
        int before = countOverloads(load, capacity);
        Map<UUID, Integer> shifted = new LinkedHashMap<>();
        Map<String, String> shiftReasons = new HashMap<>();
        Map<Sim, UUID> reassigned = new LinkedHashMap<>();
        Map<Sim, String> reassignReasons = new HashMap<>();
        Set<String> unresolvedKeys = new HashSet<>();

        for (int step = 0; step < MAX_LEVELING_STEPS; step++) {
            Overload overload = firstOverload(load, capacity, unresolvedKeys, context, range);
            if (overload == null) break;
            long day = range.start + overload.index;
            Resource resource = context.resources.get(overload.resourceId);
            List<Sim> contributors = sims.stream().filter(sim -> sim.resourceId.equals(overload.resourceId) && sim.start() <= day && sim.end() >= day).toList();
            String when = date(day).format(DAY);

            // A. Delay a contributing task within its free margin.
            Sim bestTask = null; int bestDays = 0;
            List<Sim> movable = contributors.stream().filter(sim -> movable(sim.item, focusProjectId, visibleProjects))
                    .sorted(Comparator.comparingLong((Sim sim) -> -(sim.item.row.freeFloatDays() - shifted.getOrDefault(sim.item.task.getId(), 0)))).toList();
            // Keep the smallest delay over every candidate (ties: the task with the most margin left, thanks to the sort).
            for (Sim candidate : movable) {
                long remaining = candidate.item.row.freeFloatDays() - shifted.getOrDefault(candidate.item.task.getId(), 0);
                long limit = bestTask == null ? remaining : Math.min(remaining, bestDays - 1);
                for (int d = 1; d <= limit; d++) {
                    if (shiftHelps(candidate.item.task.getId(), d, overload.resourceId, day, sims, load, capacity, range)) { bestTask = candidate; bestDays = d; break; }
                }
            }
            if (bestTask != null) {
                UUID taskId = bestTask.item.task.getId();
                for (Sim sim : sims) if (sim.item.task.getId().equals(taskId)) {
                    add(load.get(sim.resourceId), sim.start(), sim.end(), -sim.item.amount, range);
                    sim.offset += bestDays;
                    add(load.get(sim.resourceId), sim.start(), sim.end(), sim.item.amount, range);
                }
                shifted.merge(taskId, bestDays, Integer::sum);
                shiftReasons.putIfAbsent(taskId.toString(), "Réduit la surcharge de " + resource.getName() + " le " + when + " (" + describe(overload.load, capacity.get(overload.resourceId), resource) + ")");
                continue;
            }

            // B. Reassign an employee assignment to the least loaded colleague who stays at or under 100%.
            boolean done = false;
            if (resource instanceof HumanResource) {
                // Least disruptive first: assignments that clear the excess on their own, then the shortest, then the lightest.
                int excess = overload.load - capacity.get(overload.resourceId);
                List<Sim> byDisruption = contributors.stream().sorted(Comparator
                        .comparing((Sim sim) -> sim.item.amount < excess)
                        .thenComparingLong(sim -> sim.end() - sim.start())
                        .thenComparingInt(sim -> sim.item.amount)).toList();
                for (Sim sim : byDisruption) {
                    if (!reassignable(sim.item, focusProjectId, visibleProjects)) continue;
                    Set<UUID> onTask = new HashSet<>();
                    for (Sim other : sims) if (other.item.task.getId().equals(sim.item.task.getId())) onTask.add(other.resourceId);
                    UUID best = null; int bestPeak = Integer.MAX_VALUE;
                    for (Resource colleague : context.resources.values()) {
                        if (!(colleague instanceof HumanResource) || onTask.contains(colleague.getId()) || !"AVAILABLE".equalsIgnoreCase(colleague.getStatus())) continue;
                        int peak = peak(load.get(colleague.getId()), sim.start(), sim.end(), sim.item.amount, range);
                        if (peak <= 100 && peak < bestPeak) { bestPeak = peak; best = colleague.getId(); }
                    }
                    if (best != null) {
                        add(load.get(sim.resourceId), sim.start(), sim.end(), -sim.item.amount, range);
                        sim.resourceId = best;
                        add(load.get(best), sim.start(), sim.end(), sim.item.amount, range);
                        reassigned.put(sim, best);
                        reassignReasons.putIfAbsent(sim, "Supprime la surcharge de " + resource.getName() + " le " + when + " (" + describe(overload.load, 100, resource) + ")");
                        done = true;
                        break;
                    }
                }
            }
            if (!done) unresolvedKeys.add(overload.resourceId + "|" + overload.index);
        }

        int after = countOverloads(load, capacity);
        List<LevelingProposal.Action> actions = new ArrayList<>();
        Map<UUID, Item> firstItemByTask = new HashMap<>();
        for (Sim sim : sims) firstItemByTask.putIfAbsent(sim.item.task.getId(), sim.item);
        shifted.forEach((taskId, days) -> {
            Item item = firstItemByTask.get(taskId);
            actions.add(new LevelingProposal.Action("SHIFT", taskId, item.task.getName(), item.project.getName(), null, null,
                    days, item.row.forecastStart().plusDays(days), item.row.forecastEnd().plusDays(days), null, null, false, shiftReasons.get(taskId.toString())));
        });
        reassigned.forEach((sim, targetId) -> {
            if (sim.item.resource.getId().equals(targetId)) return;
            Resource target = context.resources.get(targetId);
            boolean inPool = projectAllocations.findByProjectIdAndResourceId(sim.item.project.getId(), targetId).isPresent();
            actions.add(new LevelingProposal.Action("REASSIGN", sim.item.task.getId(), sim.item.task.getName(), sim.item.project.getName(),
                    sim.item.resource.getId(), sim.item.resource.getName(), null, null, null, targetId, target.getName(), !inPool, reassignReasons.get(sim)));
        });
        return new LevelingProposal(before, after, actions, unresolvedMessages(unresolvedKeys, load, capacity, context, range));
    }

    /** Applies the actions selected by the user, all-or-nothing. */
    @Transactional
    public int applyLeveling(UUID organizationId, List<WorkloadRequests.LevelingAction> actions) {
        int applied = 0;
        for (WorkloadRequests.LevelingAction action : actions) {
            switch (action.type()) {
                case "SHIFT" -> {
                    if (action.shiftDays() == null || action.shiftDays() < 1) throw new BadRequestException(ErrorCode.INVALID_SHIFT);
                    shiftTask(taskService.requireTask(action.taskId(), organizationId), action.shiftDays(), true);
                }
                case "REASSIGN" -> {
                    if (action.fromResourceId() == null || action.toResourceId() == null) throw new BadRequestException(ErrorCode.INVALID_REASSIGNMENT);
                    reassign(organizationId, new WorkloadRequests.Reassign(action.taskId(), action.fromResourceId(), action.toResourceId()));
                }
                default -> throw new BadRequestException(ErrorCode.UNKNOWN_LEVELING_ACTION, Map.of("action", String.valueOf(action.type())));
            }
            applied++;
        }
        return applied;
    }

    private boolean shiftHelps(UUID taskId, int days, UUID overloadedResource, long day, List<Sim> sims, Map<UUID, int[]> load, Map<UUID, Integer> capacity, Range range) {
        List<Sim> taskSims = sims.stream().filter(sim -> sim.item.task.getId().equals(taskId)).toList();
        Map<UUID, int[]> trial = new HashMap<>();
        for (Sim sim : taskSims) trial.computeIfAbsent(sim.resourceId, id -> load.get(id).clone());
        for (Sim sim : taskSims) {
            int[] values = trial.get(sim.resourceId);
            add(values, sim.start(), sim.end(), -sim.item.amount, range);
            add(values, sim.start() + days, sim.end() + days, sim.item.amount, range);
        }
        int index = (int) (day - range.start);
        if (trial.get(overloadedResource)[index] >= load.get(overloadedResource)[index]) return false; // must reduce the overload
        // Never create or worsen an overload elsewhere.
        for (Map.Entry<UUID, int[]> entry : trial.entrySet()) {
            int[] before = load.get(entry.getKey()), afterValues = entry.getValue();
            int cap = capacity.get(entry.getKey());
            for (int i = 0; i < afterValues.length; i++) if (afterValues[i] > cap && afterValues[i] > before[i]) return false;
        }
        return true;
    }

    private Overload firstOverload(Map<UUID, int[]> load, Map<UUID, Integer> capacity, Set<String> ignored, Context context, Range range) {
        for (int i = 0; i < range.days; i++) {
            for (Resource resource : context.resources.values()) {
                int value = load.get(resource.getId())[i];
                if (value > capacity.get(resource.getId()) && !ignored.contains(resource.getId() + "|" + i)) return new Overload(resource.getId(), i, value);
            }
        }
        return null;
    }

    private List<String> unresolvedMessages(Set<String> keys, Map<UUID, int[]> load, Map<UUID, Integer> capacity, Context context, Range range) {
        List<String> messages = new ArrayList<>();
        for (Resource resource : context.resources.values()) {
            int[] values = load.get(resource.getId());
            int cap = capacity.get(resource.getId());
            int i = 0;
            while (i < values.length) {
                if (values[i] <= cap) { i++; continue; }
                int start = i, peak = 0;
                while (i < values.length && values[i] > cap) { peak = Math.max(peak, values[i]); i++; }
                messages.add(resource.getName() + " : surcharge du " + date(range.start + start).format(DAY) + " au " + date(range.start + i - 1).format(DAY)
                        + " (pic " + describe(peak, cap, resource) + ") sans solution automatique — réaffectez manuellement ou ajustez les dates.");
            }
        }
        return messages;
    }

    private static int countOverloads(Map<UUID, int[]> load, Map<UUID, Integer> capacity) {
        int count = 0;
        for (Map.Entry<UUID, int[]> entry : load.entrySet()) for (int value : entry.getValue()) if (value > capacity.get(entry.getKey())) count++;
        return count;
    }

    private static void add(int[] values, long start, long end, int amount, Range range) {
        for (long day = Math.max(start, range.start); day <= Math.min(end, range.end); day++) values[(int) (day - range.start)] += amount;
    }

    private static int peak(int[] values, long start, long end, int extra, Range range) {
        int peak = 0;
        for (long day = Math.max(start, range.start); day <= Math.min(end, range.end); day++) peak = Math.max(peak, values[(int) (day - range.start)] + extra);
        return peak;
    }

    private static String describe(int load, int capacity, Resource resource) {
        return resource instanceof HumanResource ? load + " %" : load + "/" + capacity + " unités";
    }

    private static boolean movable(Item item, UUID focusProjectId, Set<UUID> visibleProjects) {
        return visibleProjects.contains(item.project.getId()) && (focusProjectId == null || item.project.getId().equals(focusProjectId))
                && isNotStarted(item.task) && item.row != null && item.row.scheduled() && item.row.freeFloatDays() != null && item.row.freeFloatDays() > 0;
    }

    private static boolean reassignable(Item item, UUID focusProjectId, Set<UUID> visibleProjects) {
        return visibleProjects.contains(item.project.getId()) && (focusProjectId == null || item.project.getId().equals(focusProjectId))
                && item.task.getStatus() != TaskStatus.DONE;
    }

    // ================================================================== context

    private Context context(UUID spaceId, LocalDate today) {
        Context context = new Context();
        for (Resource resource : resources.findAllBySpaceIdOrderByTypeAscNameAsc(spaceId)) {
            Resource implementation = real(resource);
            if (implementation instanceof HumanResource || implementation instanceof EquipmentResource) context.resources.put(implementation.getId(), implementation);
        }
        Map<UUID, Map<UUID, TaskRow>> forecastByProject = new HashMap<>();
        for (TaskResourceAllocation allocation : taskAllocations.findAllByProjectResourceAllocationResourceSpaceId(spaceId)) {
            Resource resource = context.resources.get(allocation.getProjectResourceAllocation().getResource().getId());
            if (resource == null) continue;
            Task task = allocation.getTask();
            Project project = task.getProject();
            Map<UUID, TaskRow> rows = forecastByProject.computeIfAbsent(project.getId(), id -> {
                GanttResponse response = gantt.compute(project, today);
                Map<UUID, TaskRow> byTask = new HashMap<>();
                response.tasks().forEach(row -> byTask.put(row.id(), row));
                return byTask;
            });
            TaskRow row = rows.get(task.getId());
            int amount = resource instanceof HumanResource ? allocation.effectivePercent()
                    : allocation.getAllocatedQuantity() == null ? 0 : allocation.getAllocatedQuantity();
            Long start = null, end = null;
            if (row != null && row.scheduled() && row.forecastStart() != null && row.forecastEnd() != null) {
                start = row.forecastStart().toEpochDay(); end = row.forecastEnd().toEpochDay();
            }
            context.items.add(new Item(allocation, task, resource, project, amount, row, start, end));
        }
        return context;
    }

    private static Resource real(Resource resource) {
        return (Resource) Hibernate.unproxy(resource);
    }

    private static int capacity(Resource resource) {
        if (resource instanceof EquipmentResource equipment) return equipment.getQuantity() == null ? 0 : equipment.getQuantity();
        return 100;
    }

    private static int utilisation(int load, int capacity) {
        if (capacity <= 0) return load > 0 ? 999 : 0;
        return (int) Math.round(load * 100.0 / capacity);
    }

    private static boolean isNotStarted(Task task) {
        return task.getStatus() == TaskStatus.TODO && task.getStartDate() == null;
    }

    private static Range range(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from : today.minusDays(7);
        LocalDate end = to != null ? to : start.plusDays(62);
        if (end.isBefore(start)) throw new BadRequestException(ErrorCode.PERIOD_INVALID);
        if (end.toEpochDay() - start.toEpochDay() + 1 > MAX_RANGE_DAYS) end = start.plusDays(MAX_RANGE_DAYS - 1);
        return new Range(start.toEpochDay(), end.toEpochDay());
    }

    private Project requireProject(UUID id, UUID organizationId) {
        return projects.findByIdAndSpaceOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private static LocalDate date(Long epochDay) { return epochDay == null ? null : LocalDate.ofEpochDay(epochDay); }

    /** Inclusive range of epoch days. */
    private static final class Range {
        final long start, end;
        final int days;
        Range(long start, long end) { this.start = start; this.end = end; this.days = (int) (end - start + 1); }
    }

    private record Item(TaskResourceAllocation allocation, Task task, Resource resource, Project project, int amount, TaskRow row, Long start, Long end) {}

    private record Overload(UUID resourceId, int index, int load) {}

    /** Mutable copy of an assignment used while simulating a leveling proposal. */
    private static final class Sim {
        final Item item;
        UUID resourceId;
        int offset;
        Sim(Item item) { this.item = item; this.resourceId = item.resource.getId(); }
        long start() { return item.start + offset; }
        long end() { return item.end + offset; }
    }

    private static final class Context {
        final Map<UUID, Resource> resources = new LinkedHashMap<>();
        final List<Item> items = new ArrayList<>();

        List<Item> itemsOf(UUID resourceId) {
            return items.stream().filter(item -> item.resource.getId().equals(resourceId)).toList();
        }

        int loadOn(UUID resourceId, long day) {
            return items.stream().filter(item -> item.resource.getId().equals(resourceId) && item.start != null && item.start <= day && item.end >= day)
                    .mapToInt(Item::amount).sum();
        }
    }

}
