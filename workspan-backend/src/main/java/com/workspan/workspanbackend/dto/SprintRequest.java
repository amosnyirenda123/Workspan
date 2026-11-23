package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.SprintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record SprintRequest(@NotNull UUID projectId, @NotBlank @Size(max = 100) String name,
                            @Size(max = 1000) String goal, LocalDate startDate, LocalDate endDate,
                            @NotNull SprintStatus status) {}
