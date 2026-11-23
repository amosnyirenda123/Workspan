package com.workspan.workspanbackend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PasswordResetCompleteRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 128) String newPassword) {}
