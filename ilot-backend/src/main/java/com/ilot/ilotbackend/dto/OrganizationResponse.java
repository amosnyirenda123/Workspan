package com.ilot.ilotbackend.dto;
import java.time.Instant;
import java.util.UUID;
public record OrganizationResponse(
    UUID id,
    String name,
    String description,
    String website,
    String contactEmail,
    String phone,
    long spaces,
    long projects,
    long tasks,
    Instant createdAt
) {}
