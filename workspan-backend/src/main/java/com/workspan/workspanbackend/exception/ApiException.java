package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for every expected, client-facing API error. The HTTP status always comes from the
 * {@link ErrorCode}; subclasses name the status family so call sites read naturally and callers can catch
 * a whole family. {@link ApiExceptionHandler} turns these into RFC 7807 problem responses.
 */
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> params;
    private final List<FieldViolation> violations;

    public ApiException(ErrorCode code) {
        this(code, Map.of(), List.of(), null);
    }

    public ApiException(ErrorCode code, Map<String, ?> params) {
        this(code, params, List.of(), null);
    }

    protected ApiException(ErrorCode code, Map<String, ?> params, List<FieldViolation> violations, Throwable cause) {
        super(code.format(params), cause);
        this.code = code;
        this.params = params == null || params.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /** Guards against pairing a code with the wrong exception family (checked when assertions are on, e.g. in tests). */
    static ErrorCode expect(ErrorCode code, HttpStatus status) {
        assert code.status() == status : code + " is a " + code.status() + " error, not " + status;
        return code;
    }

    public ErrorCode getCode() { return code; }

    public HttpStatus getStatus() { return code.status(); }

    /** Values substituted into the message; also sent to clients so they can build localized messages. */
    public Map<String, Object> getParams() { return params; }

    /** Field-level problems, for validation failures. */
    public List<FieldViolation> getViolations() { return violations; }

    /**
     * A single invalid field.
     *
     * @param field      the property path, e.g. {@code email} or {@code employees[2].name}
     * @param constraint the constraint that failed, e.g. {@code NotBlank}, {@code Size}
     * @param message    a default English message
     * @param attributes constraint attributes useful for messages, e.g. {@code max=120}
     */
    public record FieldViolation(String field, String constraint, String message, Map<String, Object> attributes) {}
}
