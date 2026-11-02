package com.workspan.workspanbackend.dto;

import java.util.List;
import java.util.UUID;
public record AccessManagementContextResponse(UUID organizationId, boolean canManageOrganization,
                                              List<AccessScopeOption> spaces, List<AccessScopeOption> projects) {}
