package com.aicoach.common.web;

import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.PayloadTooLargeException;
import com.aicoach.common.error.ProblemDetailFactory;
import com.aicoach.common.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces a maximum request body size at the HTTP boundary.
 * <ul>
 *   <li>Declared {@code Content-Length} above the limit: rejected immediately with 413, before any
 *       controller, security or parsing work.</li>
 *   <li>Unknown length (chunked): the input stream is wrapped and throws
 *       {@link PayloadTooLargeException} once the limit is crossed; the exception handler maps it to 413.</li>
 * </ul>
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** Multipart uploads (resume PDFs, ≤ 5 MB) get a larger, path-specific limit; everything else the global one. */
    static final String UPLOAD_PATH = "/api/v1/resumes";
    static final long UPLOAD_MAX_BYTES = 5L * 1024 * 1024 + 64 * 1024;

    private final long maxBodyBytes;
    private final ProblemDetailWriter problemWriter;

    public RequestBodyLimitFilter(long maxBodyBytes, ProblemDetailWriter problemWriter) {
        this.maxBodyBytes = maxBodyBytes;
        this.problemWriter = problemWriter;
    }

    long limitFor(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && UPLOAD_PATH.equals(request.getRequestURI())
                ? Math.max(maxBodyBytes, UPLOAD_MAX_BYTES) : maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request);
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            problemWriter.write(response, ProblemDetailFactory.create(ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request body exceeds the limit of " + limit + " bytes", request.getRequestURI()));
            return;
        }
        HttpServletRequest effective = declared < 0 ? new LimitedRequest(request, limit) : request;
        chain.doFilter(effective, response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }
    }

    static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = delegate.read(buffer, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        private void add(long n) {
            count += n;
            if (count > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
