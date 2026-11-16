package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.DependencyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** lag is expressed in calendar days; a negative lag is a lead. */
public record TaskDependencyRequest(
        @NotNull UUID predecessorTaskId,
        @NotNull UUID successorTaskId,
        @NotNull DependencyType type,
        @Min(-365) @Max(365) Integer lag
) {}
