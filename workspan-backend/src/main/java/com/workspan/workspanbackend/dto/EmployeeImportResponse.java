package com.workspan.workspanbackend.dto;

import java.util.List;

public record EmployeeImportResponse(int created, int skipped, List<EmployeeImportRowResult> rows) {}
