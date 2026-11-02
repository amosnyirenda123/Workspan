package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.RoleScope;
import java.time.Instant;
import java.util.UUID;
public record RoleAssignmentResponse(UUID userId, String displayName, String email, UUID roleId,
                                     String roleName, RoleScope scope, Instant assignedAt) {}
