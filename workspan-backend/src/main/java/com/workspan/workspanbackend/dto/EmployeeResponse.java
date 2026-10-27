package com.workspan.workspanbackend.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(UUID id, UUID spaceId, String spaceName, String name, String email, String jobTitle,
                               String status, boolean hasAccount, Instant credentialEmailSentAt, Instant createdAt) {}
