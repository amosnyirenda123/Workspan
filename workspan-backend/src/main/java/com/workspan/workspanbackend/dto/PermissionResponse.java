package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.RoleScope;

import java.util.List;

public record PermissionResponse(String key, String label, String description, String category, String action, List<RoleScope> scopes) {}
