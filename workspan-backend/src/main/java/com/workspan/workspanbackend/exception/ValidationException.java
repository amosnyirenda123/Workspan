package com.workspan.workspanbackend.exception;

import jakarta.validation.ConstraintViolation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** HTTP 400 {@link ErrorCode#VALIDATION_FAILED}: one or more fields are invalid. */
public class ValidationException extends ApiException {
    private static final Set<String> IGNORED_ATTRIBUTES = Set.of("message", "groups", "payload");

    public ValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, Map.of("fields", fieldList(violations)), violations, null);
    }

    public static ValidationException of(Set<? extends ConstraintViolation<?>> violations) {
        return new ValidationException(violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(ValidationException::toViolation)
                .toList());
    }

    public static FieldViolation toViolation(ConstraintViolation<?> violation) {
        var descriptor = violation.getConstraintDescriptor();
        String constraint = descriptor == null ? "Invalid" : descriptor.getAnnotation().annotationType().getSimpleName();
        return new FieldViolation(violation.getPropertyPath().toString(), constraint, violation.getMessage(),
                descriptor == null ? Map.of() : attributes(descriptor.getAttributes()));
    }

    /** Keeps only the simple constraint attributes (max, min, value, regexp...) that help build a message. */
    public static Map<String, Object> attributes(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!IGNORED_ATTRIBUTES.contains(key) && (value instanceof Number || value instanceof String || value instanceof Boolean)) {
                out.put(key, value);
            }
        });
        return out;
    }

    private static String fieldList(List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::field).distinct().collect(Collectors.joining(", "));
    }
}
