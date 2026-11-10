package com.aicoach.common.error;

/**
 * A validation failure detected in the service layer (e.g. a referenced id does not exist). Rendered exactly like
 * Bean Validation failures: 400 {@code VALIDATION_ERROR} with an {@code errors[]} entry for the field path.
 */
public class FieldValidationException extends AppException {

    private final String path;

    public FieldValidationException(String path, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
