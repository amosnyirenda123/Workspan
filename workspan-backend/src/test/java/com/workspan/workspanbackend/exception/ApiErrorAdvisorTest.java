package com.workspan.workspanbackend.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorAdvisorTest {
    private MockMvc mvc;

    record Body(@NotBlank @Size(max = 5) String name) {}

    @RestController
    static class Endpoints {
        @GetMapping("/not-found") void notFound() { throw new NotFoundException(ErrorCode.PROJECT_NOT_FOUND); }
        @GetMapping("/conflict") void conflict() { throw new ConflictException(ErrorCode.EMPLOYEE_ALREADY_ON_TASK, Map.of("employee", "Ada")); }
        @GetMapping("/forbidden") void forbidden() { throw new ForbiddenException(ErrorCode.PERMISSION_REQUIRED, Map.of("permission", "task.edit")); }
        @GetMapping("/integrity") void integrity() { throw new DataIntegrityViolationException("FK_secret_constraint"); }
        @GetMapping("/boom") void boom() { throw new IllegalStateException("database password is hunter2"); }
        @GetMapping("/items/{id}") void item(@PathVariable UUID id) {}
        @PostMapping("/body") void body(@Valid @RequestBody Body body) {}
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new Endpoints()).setControllerAdvice(new ApiErrorAdvisor()).setValidator(validator).build();
    }

    @Test
    void apiExceptionBecomesProblemWithCodeAndStatusFromTheCatalogue() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Project not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.params").doesNotExist());
    }

    @Test
    void parametersAreInterpolatedAndExposed() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_ALREADY_ON_TASK"))
                .andExpect(jsonPath("$.detail").value("Ada is already assigned to this task"))
                .andExpect(jsonPath("$.params.employee").value("Ada"));
        mvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.params.permission").value("task.edit"));
    }

    @Test
    void beanValidationReportsEachField() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"far too long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value("name"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].constraint").value("Size"))
                .andExpect(jsonPath("$.errors[0].attributes.max").value(5));
    }

    @Test
    void malformedJsonAndBadParametersAreClientErrors() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get("/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.params.parameter").value("id"));
        mvc.perform(put("/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void infrastructureFailuresDoNotLeakInternals() throws Exception {
        mvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(content().string(not(containsString("FK_secret_constraint"))));
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void messageTemplatesFillNamedPlaceholders() {
        assertEquals("Shifting \"Build\" by 3 day(s) exceeds its free margin (1 day(s)). Recompute the proposal.",
                ErrorCode.SHIFT_EXCEEDS_MARGIN.format(Map.of("task", "Build", "days", 3, "marginType", "free", "margin", 1)));
        assertEquals("Unknown action {action}", ErrorCode.UNKNOWN_LEVELING_ACTION.format(Map.of()));
        assertEquals("Cost $5 \\ unit", ErrorCode.UNKNOWN_LEVELING_ACTION.format(Map.of("action", "$5 \\ unit")).replace("Unknown action ", "Cost "));
    }

    @Test
    void exceptionFamilyMustMatchTheCodeStatus() {
        // Surefire runs with assertions enabled, so pairing a 404 code with a 409 exception fails fast in tests.
        assertThrows(AssertionError.class, () -> new ConflictException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
