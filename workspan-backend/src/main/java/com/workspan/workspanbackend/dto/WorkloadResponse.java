package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;
import com.workspan.workspanbackend.entity.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Workload (charge) of human and equipment resources over a period, fully computed by the backend.
 * Employees: load in percent of one person's day (capacity 100). Equipment: load in units (capacity = units owned).
 * The day arrays are aligned on {@code from}: index 0 = from, index n = from + n days.
 */
public record WorkloadResponse(
        LocalDate from, LocalDate to, LocalDate today, int days,
        Summary summary,
        List<ResourceLoad> resources
) {
    public record Summary(int resourcesAnalysed, int overloadedResources, int overloadDays, int peakUtilisation, int averageUtilisation, int unscheduledAssignments) {}

    public record ResourceLoad(
            UUID resourceId, String name, ResourceType type, String jobTitle,
            /* 100 for an employee, units owned for equipment */
            int capacity,
            /* PERCENT or UNITS */
            String unit,
            /* raw load per day (percent or units) */
            List<Integer> load,
            /* load / capacity in percent, per day */
            List<Integer> utilisation,
            int peakLoad, int peakUtilisation, int averageUtilisation,
            /* overloaded days from today onwards within the period */
            int overloadDays,
            List<Period> overloads,
            List<Assignment> assignments
    ) {}

    public record Period(LocalDate start, LocalDate end, int days, int peakLoad, int peakUtilisation, List<UUID> taskIds) {}

    public record Assignment(
            UUID taskId, String taskName, UUID projectId, String projectName, boolean visible,
            TaskStatus status, LocalDate start, LocalDate end,
            /* percent for an employee, units for equipment */
            int load,
            boolean critical, Long totalFloatDays, Long freeFloatDays,
            /* the task has not started and has margin: it can be shifted */
            boolean shiftable,
            /* the assignment takes part in at least one overload period */
            boolean overloaded,
            boolean scheduled
    ) {}
}
