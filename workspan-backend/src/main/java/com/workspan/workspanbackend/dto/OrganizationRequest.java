package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 500) String description,
        @Size(max = 255) String website,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 30) String phone
) {
}
