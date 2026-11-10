package com.aicoach.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} member of every problem+json
 * response. Clients branch on these codes, never on human-readable titles or details.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token expired"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "Concurrent modification"),
    HINT_ORDER(HttpStatus.CONFLICT, "Hints must be requested in order"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already registered"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED, "Monthly interview quota reached"),
    PRO_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "Pro plan required"),
    RESUME_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "A parsed resume is required"),
    NO_QUESTIONS_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "No questions available"),
    UNSUPPORTED_FOR_MODE(HttpStatus.UNPROCESSABLE_ENTITY, "Not supported for this interview mode"),
    TOKEN_BUDGET_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI token budget exceeded"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    FEATURE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Feature disabled"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI interviewer unavailable"),
    CODE_EXECUTOR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Code execution unavailable"),
    AI_INVALID_OUTPUT(HttpStatus.BAD_GATEWAY, "AI returned an invalid response"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private static final String TYPE_BASE = "https://aicoach.dev/errors/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem type URI, e.g. {@code https://aicoach.dev/errors/payload-too-large}. */
    public String typeUri() {
        return TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Best-effort mapping for framework exceptions that only carry an HTTP status. */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL : MALFORMED_REQUEST;
        };
    }
}
