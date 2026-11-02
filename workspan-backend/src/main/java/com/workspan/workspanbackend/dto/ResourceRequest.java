package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.entity.ResourceType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ResourceRequest(
        @NotNull ResourceType type,
        @NotBlank @Size(max = 120) String name,
        @Min(1) Integer quantity,
        @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency
) {}
