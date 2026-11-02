package com.workspan.workspanbackend.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record RoleAssignmentRequest(@NotNull UUID userId, @NotNull UUID roleId) {}
