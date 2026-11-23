package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.exception.ErrorCode;

import java.util.Map;

/**
 * Outcome of importing one row. Skipped rows carry the reason as an {@link ErrorCode} name plus parameters so
 * the client can show a localized message; {@code message} is the English fallback.
 */
public record EmployeeImportRowResult(int rowNumber, String status, String message, EmployeeResponse employee,
                                      String code, Map<String, Object> params) {
    public static EmployeeImportRowResult created(int rowNumber, EmployeeResponse employee) {
        return new EmployeeImportRowResult(rowNumber, "CREATED", "Employee created", employee, null, Map.of());
    }

    public static EmployeeImportRowResult skipped(int rowNumber, ErrorCode reason, Map<String, Object> params) {
        return new EmployeeImportRowResult(rowNumber, "SKIPPED", reason.format(params), null, reason.name(), params);
    }
}
