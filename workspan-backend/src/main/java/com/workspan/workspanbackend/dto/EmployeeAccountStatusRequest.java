package com.workspan.workspanbackend.dto;
import jakarta.validation.constraints.NotNull;
public record EmployeeAccountStatusRequest(@NotNull Boolean enabled) {}
