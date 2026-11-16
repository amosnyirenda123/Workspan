package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.GanttResponse;
import com.workspan.workspanbackend.entity.BaseEntity;
import com.workspan.workspanbackend.entity.DependencyType;
import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Space;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskDependency;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskDependencyRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GanttServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskDependencyRepository dependencies = mock(TaskDependencyRepository.class);
    private final GanttService service = new GanttService(mock(ProjectRepository.class), tasks, dependencies, null);

    @Test
    void computesCriticalPathMarginsLagAndViolations() {
        Project project = project(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        Task a = task(project, "A", TaskStatus.TODO, "2026-12-01", "2026-12-05");
        Task b = task(project, "B", TaskStatus.TODO, "2026-12-06", "2026-12-10");
        Task c = task(project, "C", TaskStatus.TODO, "2026-12-01", "2026-12-03");
        Task d = task(project, "D", TaskStatus.TODO, "2026-12-11", "2026-12-12");
        TaskDependency ab = link(a, b, DependencyType.FINISH_TO_START, 0);
        TaskDependency bd = link(b, d, DependencyType.FINISH_TO_START, 2); // plan ignores the 2-day lag
        when(tasks.findAllByProjectIdOrderByNameAsc(project.getId())).thenReturn(List.of(a, b, c, d));
        when(dependencies.findAllByPredecessorTaskProjectId(project.getId())).thenReturn(List.of(ab, bd));

        GanttResponse gantt = service.compute(project, TODAY);

        GanttResponse.TaskRow rowD = row(gantt, d);
        assertEquals(LocalDate.of(2026, 12, 13), rowD.forecastStart(), "B ends on the 10th, +2 days lag => D starts on the 13th");
        assertEquals(LocalDate.of(2026, 12, 14), rowD.forecastEnd());
        assertEquals(2L, rowD.startVarianceDays());
        assertEquals(LocalDate.of(2026, 12, 14), gantt.project().forecastEnd());
        assertEquals(17L, gantt.project().projectMarginDays(), "planned end 31st - forecast end 14th");
        assertEquals(List.of(a.getId(), b.getId(), d.getId()), gantt.criticalPath());
        assertTrue(row(gantt, a).critical());
        assertEquals(0L, row(gantt, a).totalFloatDays());

        GanttResponse.TaskRow rowC = row(gantt, c);
        assertFalse(rowC.critical());
        assertEquals(11L, rowC.totalFloatDays(), "C ends on the 3rd, project forecast ends on the 14th");
        assertEquals(11L, rowC.freeFloatDays());

        assertEquals(1, gantt.project().dependencyViolations());
        assertTrue(gantt.dependencies().stream().filter(l -> l.id().equals(bd.getId())).findFirst().orElseThrow().violated());
        assertEquals("AT_RISK", gantt.project().health());
    }

    @Test
    void forecastsLateStartedTaskFromRemainingWork() {
        Project project = project(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        Task e = task(project, "E", TaskStatus.STARTED, "2026-11-01", "2026-11-10");
        e.setStartDate(LocalDate.of(2026, 11, 3));
        e.setProgress(50);
        Task f = task(project, "F", TaskStatus.TODO, "2026-11-11", "2026-11-15");
        TaskDependency ef = link(e, f, DependencyType.FINISH_TO_START, 0);
        when(tasks.findAllByProjectIdOrderByNameAsc(project.getId())).thenReturn(List.of(e, f));
        when(dependencies.findAllByPredecessorTaskProjectId(project.getId())).thenReturn(List.of(ef));

        GanttResponse gantt = service.compute(project, TODAY);

        GanttResponse.TaskRow rowE = row(gantt, e);
        // 10-day task half done => 5 days left from today (16th..20th)
        assertEquals(LocalDate.of(2026, 11, 20), rowE.forecastEnd());
        assertEquals(10L, rowE.finishVarianceDays());
        assertTrue(rowE.late());
        assertEquals("LATE", rowE.scheduleState());
        assertEquals(100, rowE.expectedProgress());

        GanttResponse.TaskRow rowF = row(gantt, f);
        assertEquals(LocalDate.of(2026, 11, 21), rowF.forecastStart());
        assertEquals(LocalDate.of(2026, 11, 25), rowF.forecastEnd());
        assertEquals(5L, gantt.project().projectMarginDays());
        // Project still ends 5 days before its deadline, but a task is late => at risk rather than late.
        assertEquals("AT_RISK", gantt.project().health());
        // E finishes late, and F (planned 11th..15th) has not started by the 16th.
        assertEquals(2, gantt.project().lateTasks());
        assertTrue(rowF.late());
    }

    @Test
    void completedTasksAreFixedOnActualDates() {
        Project project = project(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        Task g = task(project, "G", TaskStatus.DONE, "2026-11-01", "2026-11-05");
        g.setStartDate(LocalDate.of(2026, 11, 2));
        g.setEndDate(LocalDate.of(2026, 11, 8));
        g.setProgress(100);
        when(tasks.findAllByProjectIdOrderByNameAsc(project.getId())).thenReturn(List.of(g));
        when(dependencies.findAllByPredecessorTaskProjectId(project.getId())).thenReturn(List.of());

        GanttResponse.TaskRow rowG = row(service.compute(project, TODAY), g);
        assertEquals(LocalDate.of(2026, 11, 8), rowG.forecastEnd());
        assertEquals(3L, rowG.finishVarianceDays());
        assertEquals("COMPLETED_LATE", rowG.scheduleState());
        assertNull(rowG.totalFloatDays());
        assertFalse(rowG.critical());
    }

    private static GanttResponse.TaskRow row(GanttResponse gantt, Task task) {
        return gantt.tasks().stream().filter(row -> row.id().equals(task.getId())).findFirst().orElseThrow();
    }

    private static Project project(LocalDate start, LocalDate end) {
        Project project = new Project(new Space(), "P", null);
        project.setBaselineStart(start);
        project.setBaselineEnd(end);
        return withId(project);
    }

    private static Task task(Project project, String name, TaskStatus status, String plannedStart, String plannedEnd) {
        Task task = new Task(project, name);
        task.setStatus(status);
        task.setBaselineStart(LocalDate.parse(plannedStart));
        task.setBaselineEnd(LocalDate.parse(plannedEnd));
        return withId(task);
    }

    private static TaskDependency link(Task predecessor, Task successor, DependencyType type, int lag) {
        TaskDependency dependency = new TaskDependency();
        dependency.setPredecessorTask(predecessor);
        dependency.setSuccessorTask(successor);
        dependency.setType(type);
        dependency.setLag(lag);
        return withId(dependency);
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
