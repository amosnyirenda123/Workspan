package com.workspan.workspanbackend.dto;

import java.time.Instant;
import java.util.UUID;

public record SpaceResponse(UUID id, UUID organizationId, String name, Instant createdAt) {}
