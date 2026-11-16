package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * availableQuantity: equipment units still free over the task period; for an employee, the free capacity in percent
 * over the task period (100 - peak load). Employees can still be assigned above it: the result is reported as overload.
 */
public record TaskResourceOptionResponse(UUID resourceId, String name, ResourceType type,
        Integer availableQuantity, BigDecimal availableAmount, String currency, boolean assignedToTask) {}
