package com.aicoach.common.error;

import com.aicoach.common.web.RequestIdFilter;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 7807 problem details with the project's extension members ({@code code}, {@code requestId}).
 * Used by the MVC exception handler and by servlet filters / security handlers that respond before
 * the DispatcherServlet is reached, so every error has the same shape.
 */
public final class ProblemDetailFactory {

    public static final String CODE = "code";
    public static final String REQUEST_ID = "requestId";
    public static final String ERRORS = "errors";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(ErrorCode code, String detail, String instancePath) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        enrich(problem, code, instancePath);
        return problem;
    }

    /** Applies type, title, code, requestId and instance to an existing problem detail. */
    public static ProblemDetail enrich(ProblemDetail problem, ErrorCode code, String instancePath) {
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.title());
        problem.setProperty(CODE, code.name());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty(REQUEST_ID, requestId);
        }
        if (instancePath != null && problem.getInstance() == null) {
            problem.setInstance(URI.create(instancePath));
        }
        return problem;
    }
}
