package com.workspan.workspanbackend.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
public record EffectiveAccessResponse(UUID userId, UUID organizationId, boolean organizationSuperAdmin,
                                      List<String> organizationPermissions, Map<UUID, List<String>> spacePermissions,
                                      Map<UUID, List<String>> projectPermissions) {}
