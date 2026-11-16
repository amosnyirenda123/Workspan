package com.workspan.workspanbackend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Automatic workload leveling (lissage) proposal computed by the backend, reviewed then applied by the user. */
public record LevelingProposal(
        int overloadDaysBefore,
        int overloadDaysAfter,
        List<Action> actions,
        List<String> unresolved
) {
    /**
     * type = SHIFT: move the task's planned dates by shiftDays (within its free margin, so no successor moves).
     * type = REASSIGN: move the assignment of fromResource to toResource.
     */
    public record Action(
            String type,
            UUID taskId, String taskName, String projectName,
            UUID fromResourceId, String fromResourceName,
            Integer shiftDays, LocalDate newStart, LocalDate newEnd,
            UUID toResourceId, String toResourceName, boolean requiresPoolAddition,
            String reason
    ) {}
}
