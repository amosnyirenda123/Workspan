package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.DependencyType;
import com.workspan.workspanbackend.entity.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fully computed Gantt payload. Every value is calculated by the backend; the frontend only draws it.
 * Dates are inclusive calendar days. Durations, variances and margins are expressed in calendar days.
 */
public record GanttResponse(
        Summary project,
        Timeline timeline,
        List<TaskRow> tasks,
        List<Link> dependencies,
        List<UUID> criticalPath
) {
    public record Timeline(LocalDate start, LocalDate end, LocalDate today, long totalDays) {}

    public record Summary(
            UUID id, String name,
            LocalDate plannedStart, LocalDate plannedEnd,
            LocalDate actualStart, LocalDate actualEnd,
            LocalDate forecastEnd,
            /* forecastEnd - plannedEnd; positive = late */
            Long finishVarianceDays,
            /* plannedEnd - forecastEnd; positive = margin left before the project deadline */
            Long projectMarginDays,
            Long plannedDurationDays,
            int progress, int expectedProgress,
            /* progress / expectedProgress (1.0 = on schedule); null when nothing is expected yet */
            Double schedulePerformanceIndex,
            String health,
            int taskCount, int completedTasks, int lateTasks, int criticalTasks, int unscheduledTasks,
            int dependencyViolations
    ) {}

    public record TaskRow(
            UUID id, String name, TaskStatus status, int progress,
            LocalDate plannedStart, LocalDate plannedEnd, Long plannedDurationDays,
            LocalDate actualStart, LocalDate actualEnd,
            /* forecast after applying dependencies, lags, actual dates and remaining work */
            LocalDate forecastStart, LocalDate forecastEnd,
            LocalDate earlyStart, LocalDate earlyFinish, LocalDate lateStart, LocalDate lateFinish,
            /* total margin: slip allowed without delaying the project end */
            Long totalFloatDays,
            /* free margin: slip allowed without delaying any successor */
            Long freeFloatDays,
            boolean critical,
            /* actual/forecast start - planned start; positive = late */
            Long startVarianceDays,
            /* actual/forecast end - planned end; positive = late */
            Long finishVarianceDays,
            int expectedProgress,
            String scheduleState,
            boolean late,
            boolean scheduled,
            /* bar geometry relative to the timeline start, in days */
            Bar plannedBar, Bar forecastBar, Bar actualBar,
            List<String> warnings
    ) {}

    public record Bar(long offsetDays, long durationDays) {}

    public record Link(UUID id, UUID predecessorTaskId, UUID successorTaskId, DependencyType type, int lag,
                       boolean critical, boolean violated, String violation) {}
}
