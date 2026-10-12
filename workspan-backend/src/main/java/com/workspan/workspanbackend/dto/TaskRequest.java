package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record TaskRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 160) String name,
        @NotNull TaskStatus status,
        LocalDate startDate,
        LocalDate endDate
) {}
