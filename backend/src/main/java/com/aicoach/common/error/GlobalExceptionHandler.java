package com.aicoach.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single translation point from exceptions to RFC 7807 problem+json responses.
 * Framework exceptions are handled by {@link ResponseEntityExceptionHandler} and enriched with our
 * {@code code} and {@code requestId} members; unexpected exceptions become a generic 500 without
 * leaking stack traces or messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    ResponseEntity<Object> handleAppException(AppException ex, WebRequest request) {
        ErrorCode code = ex.errorCode();
        if (code.status().is5xxServerError()) {
            log.error("Application error {}: {}", code, ex.getMessage(), ex);
        } else {
            log.debug("Application error {}: {}", code, ex.getMessage());
        }
        return problem(code, ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<Object> handleRateLimited(RateLimitedException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(ErrorCode.RATE_LIMITED, ex.getMessage(), path(request));
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<Object> handleFieldValidation(FieldValidationException ex, WebRequest request) {
        return validationProblem(List.of(Map.of("path", ex.path(), "message", ex.getMessage())), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("path", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return validationProblem(errors, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return problem(ErrorCode.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return problem(ErrorCode.UNAUTHENTICATED, "Authentication is required", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL,
                "An unexpected error occurred. Quote the requestId when reporting this issue.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("path", "body." + fe.getField(), "message", message(fe)));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(Map.of("path", "body", "message", message(ge))));
        return validationProblem(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    errors.add(Map.of("path", name == null ? "parameter" : name, "message", message(err))));
        });
        return validationProblem(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        PayloadTooLargeException tooLarge = findCause(ex, PayloadTooLargeException.class);
        if (tooLarge != null) {
            return problem(ErrorCode.PAYLOAD_TOO_LARGE, tooLarge.getMessage(), request);
        }
        return problem(ErrorCode.MALFORMED_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemDetailFactory.enrich(problem, existingCode(problem, statusCode), path(request));
        }
        return response;
    }

    private ResponseEntity<Object> validationProblem(List<Map<String, String>> errors, WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.VALIDATION_ERROR, "One or more fields are invalid", path(request));
        problem.setProperty(ProblemDetailFactory.ERRORS, errors);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private ResponseEntity<Object> problem(ErrorCode code, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(code, detail, path(request));
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static ErrorCode existingCode(ProblemDetail problem, HttpStatusCode statusCode) {
        Map<String, Object> props = problem.getProperties();
        if (props != null && props.get(ProblemDetailFactory.CODE) instanceof String code) {
            return ErrorCode.valueOf(code);
        }
        return ErrorCode.fromStatus(statusCode.value());
    }

    private static String message(MessageSourceResolvable resolvable) {
        return resolvable.getDefaultMessage() == null ? "is invalid" : resolvable.getDefaultMessage();
    }

    @Nullable
    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }

    @Nullable
    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
