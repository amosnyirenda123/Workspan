package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TaskResourceAllocationRequest(@NotNull UUID resourceId, @Min(1) Integer quantity,
                                            @DecimalMin(value = "0.01") BigDecimal amount) {}
