package com.workspan.workspanbackend.dto;
import java.time.Instant;
import java.util.UUID;
public record TaskActivityResponse(UUID id, UUID taskId, UUID projectId, UUID actorId, String actorName,
                                   String action, String summary, Instant createdAt) {}
