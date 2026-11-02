package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EmployeeImportRow(
        @NotNull Integer rowNumber,
        @NotNull UUID spaceId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 100) String jobTitle
) {}
