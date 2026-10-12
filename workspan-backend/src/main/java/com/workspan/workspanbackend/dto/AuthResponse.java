package com.workspan.workspanbackend.dto;

import java.util.UUID;

public record AuthResponse(UUID userId, String displayName, String email, UUID organizationId, String organizationName) {}
