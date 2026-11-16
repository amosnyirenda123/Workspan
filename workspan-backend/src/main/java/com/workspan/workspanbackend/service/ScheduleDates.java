package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;

import java.time.LocalDate;

/**
 * Single source of truth for date semantics.
 * <ul>
 *     <li>{@code baselineStart / baselineEnd} are the <b>planned</b> dates.</li>
 *     <li>{@code startDate / endDate} are the <b>actual</b> dates (set when work starts / finishes).</li>
 * </ul>
 * The "occupancy" window used for resource booking is the actual date when known, otherwise the planned one.
 */
public final class ScheduleDates {
    private ScheduleDates() {}

    public static LocalDate taskStart(Task task) {
        return task.getStartDate() != null ? task.getStartDate() : task.getBaselineStart();
    }

    public static LocalDate taskEnd(Task task) {
        return taskEnd(task.getStartDate(), task.getEndDate(), task.getBaselineEnd());
    }

    /** Occupancy end for a given set of dates: the actual end, otherwise the planned end (never before the actual start). */
    public static LocalDate taskEnd(LocalDate actualStart, LocalDate actualEnd, LocalDate plannedEnd) {
        if (actualEnd != null) return actualEnd;
        return latest(plannedEnd, actualStart);
    }

    public static LocalDate taskStart(LocalDate actualStart, LocalDate plannedStart) {
        return actualStart != null ? actualStart : plannedStart;
    }

    public static LocalDate projectStart(Project project) {
        return earliest(project.getBaselineStart(), project.getStartDate());
    }

    public static LocalDate projectEnd(Project project) {
        return project.getEndDate() != null ? project.getEndDate() : project.getBaselineEnd();
    }

    public static LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    public static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
