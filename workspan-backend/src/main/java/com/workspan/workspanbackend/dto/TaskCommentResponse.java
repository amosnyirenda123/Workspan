package com.workspan.workspanbackend.dto;
import java.time.Instant;
import java.util.UUID;
public record TaskCommentResponse(UUID id, UUID taskId, UUID authorId, String authorName, String body, Instant createdAt) {}
