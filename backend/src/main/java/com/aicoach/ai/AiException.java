package com.aicoach.ai;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;

/** Failures of the AI layer that callers may handle (unavailable, invalid output, budget exhausted). */
public class AiException extends AppException {

    private final String logStatus;

    public AiException(ErrorCode code, String detail, String logStatus) {
        super(code, detail);
        this.logStatus = logStatus;
    }

    public AiException(ErrorCode code, String detail, String logStatus, Throwable cause) {
        super(code, detail, cause);
        this.logStatus = logStatus;
    }

    /** Value written to {@code ai_call_logs.status}. */
    public String logStatus() {
        return logStatus;
    }

    public static AiException unavailable(String detail, Throwable cause) {
        return new AiException(ErrorCode.AI_UNAVAILABLE, detail, "ERROR", cause);
    }

    /** Retryable: rate limits, 5xx, timeouts and I/O errors. */
    public static class Transient extends AiException {
        public Transient(String detail, Throwable cause) {
            super(ErrorCode.AI_UNAVAILABLE, detail, cause instanceof java.net.http.HttpTimeoutException ? "TIMEOUT" : "ERROR",
                    cause);
        }
    }
}
