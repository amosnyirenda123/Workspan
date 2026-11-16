package com.workspan.workspanbackend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID spaceId, String name, String description,
                              LocalDate baselineStart, LocalDate baselineEnd,
                              LocalDate startDate, LocalDate endDate, Instant createdAt) {}
