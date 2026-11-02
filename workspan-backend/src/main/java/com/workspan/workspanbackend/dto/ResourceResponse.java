package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;
import java.math.BigDecimal;
import java.util.UUID;

public record ResourceResponse(UUID id, UUID spaceId, String name, ResourceType type, String status,
                              Integer quantity, Integer availableQuantity, BigDecimal amount, BigDecimal consumed, BigDecimal remaining, BigDecimal availableAmount,
                              String currency, String email, String jobTitle) {}
