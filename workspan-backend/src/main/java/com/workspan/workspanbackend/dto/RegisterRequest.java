package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 50) String organizationName,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 128) String password
) {}
