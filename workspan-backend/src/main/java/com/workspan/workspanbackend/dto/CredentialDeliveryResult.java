package com.workspan.workspanbackend.dto;

import com.workspan.workspanbackend.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of sending credentials to one employee. For SKIPPED and FAILED rows, {@code code} and {@code params}
 * identify the reason (an {@link ErrorCode} name) so the client can show a localized message; {@code message}
 * is the English fallback.
 */
public record CredentialDeliveryResult(UUID employeeId, String email, String status, String message, Instant sentAt,
                                       String code, Map<String, Object> params) {
    public static CredentialDeliveryResult sent(UUID employeeId, String email, Instant sentAt) {
        return new CredentialDeliveryResult(employeeId, email, "SENT", "Temporary credentials sent", sentAt, null, Map.of());
    }

    public static CredentialDeliveryResult skipped(UUID employeeId, String email, ErrorCode reason, Instant sentAt) {
        return new CredentialDeliveryResult(employeeId, email, "SKIPPED", reason.messageTemplate(), sentAt, reason.name(), Map.of());
    }

    public static CredentialDeliveryResult failed(UUID employeeId, String email, ErrorCode reason, Map<String, Object> params) {
        return new CredentialDeliveryResult(employeeId, Objects.toString(email, ""), "FAILED", reason.format(params), null, reason.name(), params);
    }
}
