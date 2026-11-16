package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(UUID id, UUID projectId, String name, TaskStatus status,
                           LocalDate baselineStart, LocalDate baselineEnd,
                           LocalDate startDate, LocalDate endDate,
                           int progress, Instant createdAt) {}
