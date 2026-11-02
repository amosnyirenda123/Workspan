package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectResourceAllocationResponse(UUID resourceId, String name, ResourceType type,
                                                Integer quantity, BigDecimal amount, String currency,
                                                Instant allocatedAt, int activeTaskCount, int scheduledTaskCount,
                                                Integer taskAssignedQuantity, Integer scheduledTaskQuantity,
                                                BigDecimal taskAssignedAmount, BigDecimal scheduledTaskAmount, Integer freeQuantity, BigDecimal freeAmount,
                                                LocalDate startDate, LocalDate endDate, LocalDate availableFrom, boolean active) {}
