package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RoleRequest(@NotBlank @Size(max = 60) String name, @NotNull RoleScope scope,
                          @NotNull UUID contextId, @NotNull @Size(max = 30) List<String> permissionKeys) {}
