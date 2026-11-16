package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.DependencyType;

import java.util.UUID;

public record TaskDependencyResponse(UUID id, UUID predecessorTaskId, String predecessorName,
                                     UUID successorTaskId, String successorName,
                                     DependencyType type, int lag) {}
