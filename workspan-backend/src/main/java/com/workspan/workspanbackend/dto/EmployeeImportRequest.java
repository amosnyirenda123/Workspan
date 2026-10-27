package com.workspan.workspanbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmployeeImportRequest(@NotEmpty @Size(max = 500) List<EmployeeImportRow> employees) {}
