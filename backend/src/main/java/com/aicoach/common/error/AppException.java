package com.aicoach.common.error;

/**
 * Base class for all expected (business or boundary) errors. Each carries an {@link ErrorCode}
 * that determines the HTTP status and the {@code code} member of the problem+json response.
 * The detail message is safe to show to clients; never put secrets or internal state in it.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
