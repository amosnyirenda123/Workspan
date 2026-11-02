package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.RoleScope;
import java.util.List;
import java.util.UUID;
public record RoleResponse(UUID id, String name, RoleScope scope, UUID contextId, List<String> permissionKeys) {}
