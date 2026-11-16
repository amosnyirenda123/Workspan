package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.LevelingProposal;
import com.workspan.workspanbackend.dto.WorkloadResponse;
import com.workspan.workspanbackend.entity.BaseEntity;
import com.workspan.workspanbackend.entity.EquipmentResource;
import com.workspan.workspanbackend.entity.HumanResource;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.ProjectResourceAllocation;
import com.workspan.workspanbackend.entity.Resource;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskResourceAllocation;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.ProjectResourceAllocationRepository;
import com.workspan.workspanbackend.repository.ResourceRepository;
import com.workspan.workspanbackend.repository.TaskDependencyRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import com.workspan.workspanbackend.repository.TaskResourceAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkloadServiceTest {
    private final ResourceRepository resources = mock(ResourceRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskDependencyRepository dependencies = mock(TaskDependencyRepository.class);
    private final TaskResourceAllocationRepository taskAllocations = mock(TaskResourceAllocationRepository.class);
    private final ProjectResourceAllocationRepository projectAllocations = mock(ProjectResourceAllocationRepository.class);
    private final GanttService gantt = new GanttService(mock(ProjectRepository.class), tasks, dependencies, null);
    private final WorkloadService service = new WorkloadService(mock(ProjectRepository.class), resources, tasks, taskAllocations, projectAllocations, gantt, null);

    private final LocalDate base = LocalDate.now().plusDays(10);
    private Space space;
    private Project project;
    private final List<Task> projectTasks = new ArrayList<>();
    private final List<TaskResourceAllocation> assignments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        space = withId(new Space());
        project = withId(new Project(space, "Projet", null));
        project.setBaselineStart(base.minusDays(5));
        project.setBaselineEnd(base.plusDays(60));
        when(tasks.findAllByProjectIdOrderByNameAsc(project.getId())).thenReturn(projectTasks);
        when(dependencies.findAllByPredecessorTaskProjectId(project.getId())).thenReturn(List.of());
        when(taskAllocations.findAllByProjectResourceAllocationResourceSpaceId(space.getId())).thenReturn(assignments);
        when(projectAllocations.findByProjectIdAndResourceId(any(), any())).thenReturn(Optional.of(new ProjectResourceAllocation()));
    }

    @Test
    void detectsEmployeeOverloadAcrossOverlappingAssignments() {
        HumanResource alice = human("Alice");
        resources(alice);
        assign(task("T1", 0, 4), alice, 100);
        assign(task("T2", 2, 3), alice, 50);

        WorkloadResponse response = service.analyse(space.getId(), base, base.plusDays(6), null, Set.of(project.getId()));

        WorkloadResponse.ResourceLoad row = response.resources().get(0);
        assertEquals(List.of(100, 100, 150, 150, 100, 0, 0), row.load());
        assertEquals(2, row.overloadDays());
        assertEquals(150, row.peakUtilisation());
        assertEquals(1, row.overloads().size());
        assertEquals(base.plusDays(2), row.overloads().get(0).start());
        assertEquals(base.plusDays(3), row.overloads().get(0).end());
        assertEquals(1, response.summary().overloadedResources());
    }

    @Test
    void equipmentOverloadComparesUnitsWithUnitsOwned() {
        EquipmentResource drill = withId(new EquipmentResource());
        drill.setName("Perceuse"); drill.setSpace(space); drill.setQuantity(2);
        resources(drill);
        TaskResourceAllocation first = assign(task("T1", 0, 1), drill, 0); first.setAllocatedQuantity(2); first.setAllocationPercent(null);
        TaskResourceAllocation second = assign(task("T2", 1, 2), drill, 0); second.setAllocatedQuantity(1); second.setAllocationPercent(null);

        WorkloadResponse.ResourceLoad row = service.analyse(space.getId(), base, base.plusDays(2), null, Set.of(project.getId())).resources().get(0);
        assertEquals(List.of(2, 3, 1), row.load());
        assertEquals(List.of(100, 150, 50), row.utilisation());
        assertEquals(1, row.overloadDays());
    }

    @Test
    void levelingReassignsWhenNoMarginCanAbsorbTheOverload() {
        HumanResource alice = human("Alice"), bob = human("Bob");
        resources(alice, bob);
        assign(task("T1", 0, 4), alice, 100);
        assign(task("T2", 2, 3), alice, 50); // only 1 day of free margin: shifting cannot clear the overlap

        LevelingProposal proposal = service.leveling(space.getId(), null, base.plusDays(30), null, Set.of(project.getId()));

        assertEquals(2, proposal.overloadDaysBefore());
        assertEquals(0, proposal.overloadDaysAfter());
        assertEquals(1, proposal.actions().size());
        LevelingProposal.Action action = proposal.actions().get(0);
        assertEquals("REASSIGN", action.type());
        assertEquals("T2", action.taskName());
        assertEquals(alice.getId(), action.fromResourceId());
        assertEquals(bob.getId(), action.toResourceId());
        assertTrue(proposal.unresolved().isEmpty());
    }

    @Test
    void levelingShiftsWithinFreeMarginWhenColleaguesAreBusy() {
        HumanResource alice = human("Alice"), bob = human("Bob");
        resources(alice, bob);
        assign(task("A1", 0, 1), alice, 100);
        assign(task("A2", 0, 1), alice, 100);
        assign(task("B1", 0, 9), bob, 100); // Bob is fully busy and makes the project last 10 days

        LevelingProposal proposal = service.leveling(space.getId(), null, base.plusDays(30), null, Set.of(project.getId()));

        assertEquals(2, proposal.overloadDaysBefore());
        assertEquals(0, proposal.overloadDaysAfter());
        assertEquals(1, proposal.actions().size(), "a single task delayed by 2 days is enough");
        LevelingProposal.Action action = proposal.actions().get(0);
        assertEquals("SHIFT", action.type());
        assertEquals(2, action.shiftDays());
        assertEquals(base.plusDays(2), action.newStart());
        assertEquals(base.plusDays(3), action.newEnd());
    }

    @Test
    void reportsOverloadThatCannotBeSolvedAutomatically() {
        HumanResource alice = human("Alice");
        resources(alice);
        // Started today with a 14-day plan: its forecast (today .. today+13) covers T1 (base .. base+3).
        Task started = task("S", -10, 3);
        started.setStatus(TaskStatus.STARTED); started.setStartDate(LocalDate.now());
        assign(task("T1", 0, 3), alice, 100);
        assign(started, alice, 100);

        LevelingProposal proposal = service.leveling(space.getId(), null, base.plusDays(30), null, Set.of(project.getId()));
        assertFalse(proposal.unresolved().isEmpty());
        assertTrue(proposal.overloadDaysAfter() > 0);
    }

    // ------------------------------------------------------------------ fixtures

    private HumanResource human(String name) {
        HumanResource resource = withId(new HumanResource());
        resource.setName(name); resource.setSpace(space);
        return resource;
    }

    private void resources(Resource... list) {
        when(resources.findAllBySpaceIdOrderByTypeAscNameAsc(space.getId())).thenReturn(List.of(list));
    }

    private Task task(String name, int startOffset, int endOffset) {
        Task task = withId(new Task(project, name));
        task.setBaselineStart(base.plusDays(startOffset));
        task.setBaselineEnd(base.plusDays(endOffset));
        projectTasks.add(task);
        return task;
    }

    private TaskResourceAllocation assign(Task task, Resource resource, int percent) {
        ProjectResourceAllocation pool = withId(new ProjectResourceAllocation());
        pool.setProject(project); pool.setResource(resource);
        TaskResourceAllocation allocation = withId(new TaskResourceAllocation());
        allocation.setTask(task); allocation.setProjectResourceAllocation(pool); allocation.setAllocationPercent(percent);
        assignments.add(allocation);
        return allocation;
    }

    private static <T extends BaseEntity> T withId(T entity) {
        try {
            Field id = BaseEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(entity, UUID.randomUUID());
            return entity;
        } catch (ReflectiveOperationException cause) {
            throw new IllegalStateException(cause);
        }
    }
}
