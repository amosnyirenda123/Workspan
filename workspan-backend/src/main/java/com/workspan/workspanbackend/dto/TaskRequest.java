package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * baselineStart / baselineEnd = planned dates.
 * startDate / endDate = actual dates (optional; filled automatically on status transitions when omitted).
 */
public record TaskRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 160) String name,
        @NotNull TaskStatus status,
        LocalDate baselineStart,
        LocalDate baselineEnd,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) @Max(100) Integer progress
) {}
