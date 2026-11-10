package com.aicoach.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reusesWellFormedIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdFilter.HEADER, "lb-01JC3Z6V4W8Q2N7Y");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("lb-01JC3Z6V4W8Q2N7Y");
        assertThat(mdcDuringRequest.get()).isEqualTo("lb-01JC3Z6V4W8Q2N7Y");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo("lb-01JC3Z6V4W8Q2N7Y");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("MDC is cleared after the request").isNull();
    }

    @Test
    void generatesIdWhenAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void replacesMalformedOrHostileIds() {
        assertThat(RequestIdFilter.resolve("short")).isNotEqualTo("short");
        assertThat(RequestIdFilter.resolve("abc\n2026-01-01 ERROR forged log line"))
                .doesNotContain("\n").hasSize(36);
        assertThat(RequestIdFilter.resolve("x".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.resolve(null)).hasSize(36);
    }
}
