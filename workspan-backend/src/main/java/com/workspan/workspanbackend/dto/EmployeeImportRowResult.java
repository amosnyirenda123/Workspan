package com.workspan.workspanbackend.dto;

public record EmployeeImportRowResult(int rowNumber, String status, String message, EmployeeResponse employee) {}
