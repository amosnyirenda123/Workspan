package com.workspan.workspanbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Request bodies used to redistribute workload. */
public final class WorkloadRequests {
    private WorkloadRequests() {}

    public record Reassign(@NotNull UUID taskId, @NotNull UUID fromResourceId, @NotNull UUID toResourceId) {}

    /** Delay a task that has not started by a number of days, within its total margin. */
    public record Shift(@NotNull UUID taskId, @NotNull @Min(1) @Max(365) Integer days) {}

    public record LevelingAction(@NotBlank String type, @NotNull UUID taskId, UUID fromResourceId, UUID toResourceId, Integer shiftDays) {}

    public record ApplyLeveling(@NotEmpty List<@Valid LevelingAction> actions) {}

    public record Candidate(UUID resourceId, String name, String jobTitle, boolean inProjectPool,
                            int peakUtilisationWithTask, int averageUtilisation, boolean fits) {}
}
