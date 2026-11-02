package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;
import java.math.BigDecimal;
import java.util.UUID;

public record TaskResourceOptionResponse(UUID resourceId, String name, ResourceType type,
        Integer availableQuantity, BigDecimal availableAmount, String currency, boolean assignedToTask) {}
