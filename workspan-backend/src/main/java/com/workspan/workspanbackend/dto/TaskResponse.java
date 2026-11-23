package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.entity.TaskPriority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(UUID id, UUID projectId, UUID sprintId, String name, TaskStatus status,
                           TaskPriority priority, Integer estimatePoints, LocalDate dueDate,
                           LocalDate baselineStart, LocalDate baselineEnd,
                           LocalDate startDate, LocalDate endDate,
                           int progress, Instant createdAt) {}
