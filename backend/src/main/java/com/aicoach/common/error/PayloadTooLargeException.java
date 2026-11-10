package com.aicoach.common.error;

/** Thrown when a request body exceeds the configured limit while it is being read. */
public class PayloadTooLargeException extends AppException {

    public PayloadTooLargeException(long limitBytes) {
        super(ErrorCode.PAYLOAD_TOO_LARGE, "Request body exceeds the limit of " + limitBytes + " bytes");
    }
}
