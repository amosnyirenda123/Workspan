package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/** Schedule-only update issued from the Gantt view (planned dates, actual dates, progress). */
public record TaskScheduleRequest(
        LocalDate baselineStart,
        LocalDate baselineEnd,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) @Max(100) Integer progress
) {}
