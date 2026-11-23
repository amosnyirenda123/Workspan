package com.workspan.workspanbackend.exception;

import com.workspan.workspanbackend.exception.ApiException.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import jakarta.validation.ConstraintViolation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns every exception leaving a controller into an RFC 7807 problem response:
 * <pre>
 * {
 *   "type": "about:blank", "title": "Conflict", "status": 409,
 *   "detail": "These tasks are already linked",   // English, for logs and API users
 *   "instance": "/api/tasks/dependencies",
 *   "code": "TASKS_ALREADY_LINKED",               // stable key the frontend translates
 *   "params": { ... },                            // values for the message placeholders (optional)
 *   "errors": [ { "field", "constraint", "message", "attributes" } ],  // validation only
 *   "timestamp": "2026-11-23T10:15:30Z"
 * }
 * </pre>
 * Unexpected exceptions are logged with their stack trace and answered with a generic
 * {@link ErrorCode#INTERNAL_ERROR} so no internal detail reaches the client.
 */
@RestControllerAdvice
public class ApiErrorAdvisor extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvisor.class);

    // ---------------------------------------------------------------- application exceptions

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            log.error("{} {} failed with {}: {}", request.getMethod(), request.getRequestURI(), exception.getCode(), exception.getMessage(), exception);
        } else {
            log.debug("{} {} rejected with {}: {}", request.getMethod(), request.getRequestURI(), exception.getCode(), exception.getMessage());
        }
        return respond(exception.getCode(), exception.getMessage(), exception.getParams(), exception.getViolations(), request.getRequestURI());
    }

    /** Programmatic bean validation ({@code validator.validate} + {@code @Validated} services). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return handleApiException(ValidationException.of(exception.getConstraintViolations()), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleConcurrentModification(RuntimeException exception, HttpServletRequest request) {
        log.info("{} {} hit a concurrent modification: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.info("{} {} violated a data constraint: {}", request.getMethod(), request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DATA_CONFLICT, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return respond(ErrorCode.INTERNAL_ERROR, request.getRequestURI());
    }

    // ---------------------------------------------------------------- Spring MVC exceptions

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            violations.add(error.isBindingFailure()
                    ? new FieldViolation(error.getField(), "TypeMismatch", error.getDefaultMessage(), Map.of())
                    : toViolation(error.getField(), error, unwrap(error)));
        }
        for (ObjectError error : exception.getBindingResult().getGlobalErrors()) {
            violations.add(toViolation(error.getObjectName(), error, unwrap(error)));
        }
        return asObject(validationFailed(violations, request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            String name = parameter == null ? "parameter" : parameter;
            result.getResolvableErrors().forEach(error -> {
                ConstraintViolation<?> violation = null;
                try { violation = result.unwrap(error, ConstraintViolation.class); } catch (IllegalArgumentException ignored) { /* not a bean-validation error */ }
                violations.add(toViolation(name, error, violation));
            });
        });
        return asObject(validationFailed(violations, request));
    }

    /** Every other MVC exception (bad JSON, wrong method, type mismatch, unknown URL...) goes through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ErrorCode code = codeFor(exception, status);
        Map<String, Object> params = paramsFor(exception);
        if (status.is5xxServerError()) log.error("MVC error on {}: {}", path(request), exception.getMessage(), exception);
        else log.debug("MVC rejected {} with {}: {}", path(request), code, exception.getMessage());
        ResponseEntity<ProblemDetail> response = respond(code, code.format(params), params, List.of(), path(request));
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    private static ErrorCode codeFor(Exception exception, HttpStatusCode status) {
        if (exception instanceof HttpMessageNotReadableException) return ErrorCode.MALFORMED_REQUEST;
        if (exception instanceof TypeMismatchException) return ErrorCode.INVALID_PARAMETER;
        if (exception instanceof MissingPathVariableException) return ErrorCode.INTERNAL_ERROR;
        if (exception instanceof MissingServletRequestParameterException
                || exception instanceof MissingServletRequestPartException
                || exception instanceof MissingRequestValueException) return ErrorCode.MISSING_PARAMETER;
        if (exception instanceof HttpRequestMethodNotSupportedException) return ErrorCode.METHOD_NOT_ALLOWED;
        if (exception instanceof HttpMediaTypeNotSupportedException) return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        if (exception instanceof HttpMediaTypeNotAcceptableException) return ErrorCode.NOT_ACCEPTABLE;
        if (exception instanceof NoHandlerFoundException || exception instanceof NoResourceFoundException) return ErrorCode.ENDPOINT_NOT_FOUND;
        if (exception instanceof MaxUploadSizeExceededException) return ErrorCode.PAYLOAD_TOO_LARGE;
        if (exception instanceof AsyncRequestTimeoutException) return ErrorCode.SERVICE_UNAVAILABLE;
        return ErrorCode.forStatus(status);
    }

    private static Map<String, Object> paramsFor(Exception exception) {
        if (exception instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
            return Map.of("parameter", mismatch.getPropertyName());
        }
        if (exception instanceof MissingServletRequestParameterException missing) return Map.of("parameter", missing.getParameterName());
        if (exception instanceof MissingServletRequestPartException missing) return Map.of("parameter", missing.getRequestPartName());
        return Map.of();
    }

    // ---------------------------------------------------------------- response building

    private ResponseEntity<ProblemDetail> validationFailed(List<FieldViolation> violations, WebRequest request) {
        ValidationException exception = new ValidationException(violations);
        log.debug("Validation failed on {}: {}", path(request), exception.getMessage());
        return respond(exception.getCode(), exception.getMessage(), exception.getParams(), violations, path(request));
    }

    private static ConstraintViolation<?> unwrap(ObjectError error) {
        return error.contains(ConstraintViolation.class) ? error.unwrap(ConstraintViolation.class) : null;
    }

    private static FieldViolation toViolation(String field, MessageSourceResolvable error, ConstraintViolation<?> violation) {
        if (violation != null && violation.getConstraintDescriptor() != null) {
            var descriptor = violation.getConstraintDescriptor();
            return new FieldViolation(field, descriptor.getAnnotation().annotationType().getSimpleName(), violation.getMessage(),
                    ValidationException.attributes(descriptor.getAttributes()));
        }
        // Codes look like ["NotBlank.request.name", ..., "NotBlank"]: the last one is the bare constraint name.
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
        return new FieldViolation(field, constraint, error.getDefaultMessage(), Map.of());
    }

    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String path) {
        return respond(code, code.messageTemplate(), Map.of(), List.of(), path);
    }

    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, Map<String, Object> params,
                                                         List<FieldViolation> violations, String path) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        if (path != null) {
            try { problem.setInstance(URI.create(path)); } catch (IllegalArgumentException ignored) { /* unusual characters: omit */ }
        }
        problem.setProperty("code", code.name());
        if (!params.isEmpty()) problem.setProperty("params", params);
        if (!violations.isEmpty()) problem.setProperty("errors", violations);
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(problem);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Object> asObject(ResponseEntity<ProblemDetail> response) {
        return (ResponseEntity) response;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
