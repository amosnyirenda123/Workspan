package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalogue of every error the API can return.
 * <p>
 * Each code fixes the HTTP status sent to the client and a default English message. Messages may contain
 * named placeholders such as {@code {task}} that are filled from the exception's parameters. The code name
 * and parameters are sent to the client, which uses them to show its own localized message, so a code's
 * name and placeholders form part of the API contract: rename them only together with the frontend
 * catalogue in {@code workspan-frontend/src/api/errors.ts}.
 */
public enum ErrorCode {
    // ---------------------------------------------------------------- generic / framework
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid fields: {fields}"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body is missing or malformed"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "The value of parameter '{parameter}' is invalid"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required parameter '{parameter}' is missing"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is invalid"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested item was not found"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "No endpoint exists at this address"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This operation is not supported on this address"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested response format is not supported"),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the data"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "The item conflicts with existing data or is still referenced by another item"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "This item was changed by someone else; reload it and try again"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request is too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request content type is not supported"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable"),

    // ---------------------------------------------------------------- authentication
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Change the temporary password before continuing"),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "Choose a different password"),
    ACCOUNT_EMAIL_TAKEN(HttpStatus.CONFLICT, "An account with this email already exists"),
    ORGANIZATION_NAME_TAKEN(HttpStatus.CONFLICT, "An organization with this name already exists"),

    // ---------------------------------------------------------------- authorization
    ORGANIZATION_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "Organization administrator access required"),
    SPACE_PERMISSION_REQUIRED(HttpStatus.FORBIDDEN, "A space permission is required"),
    PROJECT_PERMISSION_REQUIRED(HttpStatus.FORBIDDEN, "A project permission is required"),
    RESOURCE_POOL_PERMISSION_REQUIRED(HttpStatus.FORBIDDEN, "Permission required to view this resource pool"),
    PERMISSION_REQUIRED(HttpStatus.FORBIDDEN, "Permission required: {permission}"),
    SELF_ROLE_ASSIGNMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "You cannot assign a role to your own account"),

    // ---------------------------------------------------------------- roles & permissions
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Role not found"),
    ACTIVE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Active organization member not found"),
    RESERVED_ROLE_NAME(HttpStatus.BAD_REQUEST, "This role name is reserved for the platform"),
    ROLE_NAME_TAKEN(HttpStatus.CONFLICT, "A role with this name already exists at this scope in the organization"),
    BUILT_IN_ROLE_IMMUTABLE(HttpStatus.CONFLICT, "Built-in roles cannot be changed"),
    BUILT_IN_ROLE_UNDELETABLE(HttpStatus.CONFLICT, "Built-in roles cannot be deleted"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "Remove this role from its users before deleting it"),
    SUPER_ADMIN_ASSIGNMENT_PROTECTED(HttpStatus.CONFLICT, "The organization super administrator assignment is protected"),
    SUPER_ADMIN_NOT_ASSIGNABLE(HttpStatus.BAD_REQUEST, "Organization super administrator access cannot be assigned through role management"),
    ROLE_SCOPE_IMMUTABLE(HttpStatus.BAD_REQUEST, "A role's scope cannot be changed"),
    ROLE_REQUIRED_FOR_SCOPE(HttpStatus.BAD_REQUEST, "Choose a role for this scope"),
    PERMISSION_SCOPE_MISMATCH(HttpStatus.BAD_REQUEST, "This permission cannot be used at the selected role scope"),
    GANTT_PERMISSIONS_REQUIRE_VIEW(HttpStatus.BAD_REQUEST, "Gantt schedule and dependency permissions require gantt.view"),
    WORKLOAD_MANAGE_REQUIRES_VIEW(HttpStatus.BAD_REQUEST, "Workload management permission requires workload.view"),
    UNKNOWN_PERMISSION(HttpStatus.BAD_REQUEST, "One or more permissions are not in the permission catalogue"),

    // ---------------------------------------------------------------- organizations & spaces
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Organization not found"),
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Space not found"),
    SPACE_NOT_IN_ORGANIZATION(HttpStatus.BAD_REQUEST, "Space not found in this organization"),

    // ---------------------------------------------------------------- employees & credentials
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "Employee not found"),
    EMPLOYEE_NOT_IN_SPACE(HttpStatus.NOT_FOUND, "Employee not found in this space"),
    EMPLOYEE_ALREADY_HAS_ACCOUNT(HttpStatus.CONFLICT, "This employee already has an account"),
    EMPLOYEE_EMAIL_MISSING(HttpStatus.BAD_REQUEST, "Employee email is missing; add an email before sending credentials"),
    EMPLOYEE_EMAIL_INVALID(HttpStatus.BAD_REQUEST, "A valid employee email is required"),
    EMPLOYEE_EMAIL_LOCKED(HttpStatus.CONFLICT, "Email cannot be changed after an account has been created"),
    EMPLOYEE_EMAIL_ACCOUNT_MISMATCH(HttpStatus.CONFLICT, "Employee email does not match the linked account"),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "This email already belongs to another employee or account"),
    EMPLOYEE_MANAGED_IN_DIRECTORY(HttpStatus.BAD_REQUEST, "Employees are managed through the employee directory"),
    INVITATION_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "Choose an access role before inviting this employee"),
    INVITATION_ROLE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "Choose a role available for this employee's scope"),
    INVITATION_ROLE_NOT_CUSTOM(HttpStatus.BAD_REQUEST, "Choose a custom access role"),
    INVITATION_ROLE_EMPTY(HttpStatus.BAD_REQUEST, "Choose a role with at least one permission"),
    INVITATION_ORGANIZATION_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "Choose an organization role"),
    INVITATION_SPACE_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "Choose a role for the employee's space"),
    INVITATION_PROJECT_ROLE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Project roles cannot be assigned during employee invitations"),
    INVITATION_REQUIRED(HttpStatus.BAD_REQUEST, "Choose an access role and invite this employee individually"),
    CREDENTIALS_ALREADY_SENT(HttpStatus.CONFLICT, "Credentials were already sent"),
    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery failed. Check the SMTP settings and retry."),
    CREDENTIAL_DELIVERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Could not send credentials. Retry this employee."),

    // ---------------------------------------------------------------- resources
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    RESOURCE_NOT_IN_SPACE(HttpStatus.NOT_FOUND, "Resource not found in this space"),
    RESOURCE_UNAVAILABLE(HttpStatus.CONFLICT, "This resource is not available"),
    RESOURCE_ASSIGNED_TO_PROJECTS(HttpStatus.CONFLICT, "Remove this resource from its projects before deleting it"),
    EQUIPMENT_FIELDS_INVALID(HttpStatus.BAD_REQUEST, "Equipment requires a quantity and does not accept a budget amount"),
    BUDGET_FIELDS_INVALID(HttpStatus.BAD_REQUEST, "Budget resources require an amount and do not accept a quantity"),

    // ---------------------------------------------------------------- projects & project resource pool
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
    PROJECT_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Project resource assignment not found"),
    PLANNED_DATES_INVALID(HttpStatus.BAD_REQUEST, "The planned end date cannot be before the planned start date"),
    PROJECT_HAS_RESOURCES(HttpStatus.CONFLICT, "Remove project resources before moving it to another space"),
    PROJECT_DATES_EXCEED_EQUIPMENT(HttpStatus.CONFLICT, "Project dates exceed equipment availability for {resource}"),
    PROJECT_STARTS_AFTER_TASK(HttpStatus.BAD_REQUEST, "A project cannot be planned to start after the planned start of task {task}"),
    PROJECT_ENDS_BEFORE_TASK(HttpStatus.BAD_REQUEST, "A project cannot be planned to end before the planned end of task {task}"),
    PROJECT_ENDED_RESOURCE_LOCKED(HttpStatus.CONFLICT, "Resources cannot be assigned to a project that has ended"),
    PROJECT_ENDED_BUDGET_LOCKED(HttpStatus.CONFLICT, "Budget cannot be withdrawn after the project has ended"),
    RESOURCE_OUTSIDE_PROJECT_SPACE(HttpStatus.BAD_REQUEST, "A project can only use resources from its own space"),
    RESOURCE_ALREADY_IN_PROJECT_POOL(HttpStatus.CONFLICT, "This resource is already in the project pool"),
    RESOURCE_IN_USE_BY_TASKS(HttpStatus.CONFLICT, "Remove this resource from its tasks before removing it from the project"),
    EQUIPMENT_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "This allocation exceeds the available equipment quantity"),
    BUDGET_EXCEEDED(HttpStatus.CONFLICT, "This allocation exceeds the remaining budget"),
    EMPLOYEE_POOL_ALLOCATION_INVALID(HttpStatus.BAD_REQUEST, "An employee is added to a project pool as one person"),
    EQUIPMENT_QUANTITY_REQUIRED(HttpStatus.BAD_REQUEST, "Choose the number of equipment units to allocate"),
    BUDGET_AMOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Choose a budget amount to allocate"),

    // ---------------------------------------------------------------- tasks & task resources
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task not found"),
    TASK_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Task resource assignment not found"),
    TASK_HAS_DEPENDENCIES(HttpStatus.CONFLICT, "Remove this task's dependencies before moving it to another project"),
    TASK_BUDGET_COMMITTED(HttpStatus.CONFLICT, "A completed task with committed budget cannot be deleted"),
    ACTUAL_START_IN_FUTURE(HttpStatus.BAD_REQUEST, "The actual start date cannot be in the future"),
    ACTUAL_END_IN_FUTURE(HttpStatus.BAD_REQUEST, "The actual end date cannot be in the future"),
    ACTUAL_DATES_INVALID(HttpStatus.BAD_REQUEST, "The actual end date cannot be before the actual start date"),
    PROGRESS_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Progress must be between 0 and 100"),
    TASK_STARTS_BEFORE_PROJECT(HttpStatus.BAD_REQUEST, "The task's planned start cannot be before the project's planned start"),
    TASK_ENDS_AFTER_PROJECT(HttpStatus.BAD_REQUEST, "The task's planned end cannot be after the project's planned end"),
    TASK_STARTS_AFTER_PROJECT_END(HttpStatus.BAD_REQUEST, "The task's planned start cannot be after the project's planned end"),
    RESOURCE_NOT_IN_PROJECT_POOL(HttpStatus.BAD_REQUEST, "Assign the resource to the project pool first"),
    TASK_RESOURCE_NOT_IN_TARGET_POOL(HttpStatus.CONFLICT, "The task's resource must be in the target project's pool"),
    TASK_COMPLETED_RESOURCE_LOCKED(HttpStatus.CONFLICT, "Resources cannot be assigned to a completed task"),
    TASK_PERIOD_ENDED(HttpStatus.CONFLICT, "Resources cannot be assigned to a task whose scheduled period has ended"),
    TASK_ENDED_BUDGET_LOCKED(HttpStatus.CONFLICT, "Budget cannot be withdrawn after the task has ended"),
    RESOURCE_ALREADY_ON_TASK(HttpStatus.CONFLICT, "This resource is already assigned to the task"),
    EMPLOYEE_TASK_ALLOCATION_INVALID(HttpStatus.BAD_REQUEST, "An employee is assigned with a percentage of their time"),
    PROJECT_UNITS_EXCEEDED(HttpStatus.CONFLICT, "This allocation exceeds the units allocated to the project"),
    PROJECT_BUDGET_EXCEEDED(HttpStatus.CONFLICT, "This allocation exceeds the project's available budget"),
    BUDGET_AMOUNT_NOT_POSITIVE(HttpStatus.BAD_REQUEST, "The budget amount must be greater than zero"),

    // ---------------------------------------------------------------- dependencies & scheduling
    DEPENDENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Dependency not found"),
    TASKS_ALREADY_LINKED(HttpStatus.CONFLICT, "These tasks are already linked"),
    DEPENDENCY_CYCLE(HttpStatus.CONFLICT, "This dependency would create a cycle"),
    SELF_DEPENDENCY(HttpStatus.BAD_REQUEST, "A task cannot depend on itself"),
    DEPENDENCY_CROSS_PROJECT(HttpStatus.BAD_REQUEST, "Both tasks must belong to the same project"),
    TASK_RESCHEDULE_BLOCKED(HttpStatus.CONFLICT, "Cannot reschedule task \"{task}\": {reason}"),

    // ---------------------------------------------------------------- workload
    TASK_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Task assignment not found"),
    PERIOD_INVALID(HttpStatus.BAD_REQUEST, "The end of the period cannot be before its start"),
    ONLY_EMPLOYEES_REASSIGNABLE(HttpStatus.BAD_REQUEST, "Only employee assignments can be reassigned"),
    COMPLETED_TASK_NOT_REASSIGNABLE(HttpStatus.CONFLICT, "A completed task cannot be reassigned"),
    SAME_EMPLOYEE_REASSIGNMENT(HttpStatus.BAD_REQUEST, "Choose another employee"),
    EMPLOYEE_UNAVAILABLE(HttpStatus.CONFLICT, "This employee is not available"),
    EMPLOYEE_ALREADY_ON_TASK(HttpStatus.CONFLICT, "{employee} is already assigned to this task"),
    TASK_ALREADY_STARTED(HttpStatus.CONFLICT, "Only a task that has not started can be shifted: \"{task}\""),
    TASK_NOT_SCHEDULED(HttpStatus.BAD_REQUEST, "The task \"{task}\" has no planned dates"),
    SHIFT_EXCEEDS_MARGIN(HttpStatus.CONFLICT, "Shifting \"{task}\" by {days} day(s) exceeds its {marginType} margin ({margin} day(s)). Recompute the proposal."),
    INVALID_SHIFT(HttpStatus.BAD_REQUEST, "Invalid shift"),
    INVALID_REASSIGNMENT(HttpStatus.BAD_REQUEST, "Invalid reassignment"),
    UNKNOWN_LEVELING_ACTION(HttpStatus.BAD_REQUEST, "Unknown action {action}");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final HttpStatus status;
    private final String messageTemplate;

    ErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus status() { return status; }

    public String messageTemplate() { return messageTemplate; }

    /** Fills the message template; placeholders without a parameter are left as-is. */
    public String format(Map<String, ?> params) {
        if (params == null || params.isEmpty()) return messageTemplate;
        Matcher matcher = PLACEHOLDER.matcher(messageTemplate);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = params.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The generic code used when only an HTTP status is known (framework exceptions, unexpected statuses). */
    public static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
