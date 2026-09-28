package com.ilot.ilotbackend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record OrganizationRequest(@NotBlank @Size(max=50) String name) {}
