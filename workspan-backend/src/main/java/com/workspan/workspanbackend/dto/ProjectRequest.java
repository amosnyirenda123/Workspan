package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** baselineStart / baselineEnd are the planned project dates. Actual dates are derived from task execution. */
public record ProjectRequest(
        @NotNull UUID spaceId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 10000) String description,
        LocalDate baselineStart,
        LocalDate baselineEnd
) {}
