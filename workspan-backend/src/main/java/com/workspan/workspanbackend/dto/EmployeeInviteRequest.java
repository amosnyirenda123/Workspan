package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.RoleScope;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmployeeInviteRequest(@NotNull UUID roleId, @NotNull RoleScope roleScope) {}
