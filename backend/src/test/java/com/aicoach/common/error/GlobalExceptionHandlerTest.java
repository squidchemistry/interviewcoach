package com.aicoach.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    record CreateThing(@NotBlank String name) {
    }

    @RestController
    static class TestController {
        @GetMapping("/not-found")
        String notFound() {
            throw new AppException(ErrorCode.NOT_FOUND, "Thing 42 was not found");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("bad");
        }

        @GetMapping("/unavailable")
        String unavailable() {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Upstream unavailable");
        }

        @GetMapping("/param")
        String param(@RequestParam @Max(10) int size) {
            return "ok" + size;
        }

        @PostMapping(path = "/things", consumes = MediaType.APPLICATION_JSON_VALUE)
        String create(@Valid @RequestBody CreateThing body) {
            return body.name();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void appExceptionBecomesProblemDetailWithCodeAndRequestId() throws Exception {
        mvc.perform(get("/not-found").header(RequestIdFilter.HEADER, "req-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("https://aicoach.dev/errors/not-found"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Thing 42 was not found"))
                .andExpect(jsonPath("$.requestId").value("req-12345678"))
                .andExpect(jsonPath("$.instance").value("/not-found"));
    }

    @Test
    void unexpectedExceptionIsGeneric500WithoutLeakingInternals() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.aicoach"))));
    }

    @Test
    void server5xxAppExceptionKeepsItsCode() throws Exception {
        mvc.perform(get("/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void beanValidationFailureListsFieldPaths() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].path").value("body.name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void methodParameterValidationFailureListsParameterName() throws Exception {
        mvc.perform(get("/param").param("size", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].path").value("size"));
    }

    @Test
    void malformedJsonIsMalformedRequest() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void frameworkErrorsAreEnrichedWithCodes() throws Exception {
        mvc.perform(post("/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").exists());
        mvc.perform(post("/things").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void securityExceptionsMapTo401And403() throws Exception {
        mvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
