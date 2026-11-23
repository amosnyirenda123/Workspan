package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.SprintStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintResponse(UUID id, UUID projectId, String name, String goal, LocalDate startDate,
                             LocalDate endDate, SprintStatus status, int taskCount, int completedTaskCount,
                             Instant createdAt) {}
