package com.aicoach.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.common.error.PayloadTooLargeException;
import com.aicoach.common.error.ProblemDetailWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodyLimitFilterTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(10, new ProblemDetailWriter(mapper));

    @Test
    void rejectsDeclaredLengthAboveLimitWithoutCallingChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/things");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                chainCalled.set(true);
            }
        });

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(body.get("instance").asText()).isEqualTo("/api/v1/things");
    }

    @Test
    void passesRequestsWithinLimitUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/things");
        request.setContent(new byte[10]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void streamingBodyWithUnknownLengthFailsOnceLimitIsCrossed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/things") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked transfer encoding
            }
        };
        request.setContent("0123456789ABC".getBytes());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ServletRequest wrapped = chain.getRequest();
        assertThat(wrapped).isNotSameAs(request);
        byte[] buffer = new byte[8];
        assertThat(wrapped.getInputStream().read(buffer, 0, 8)).isEqualTo(8);
        assertThat(wrapped.getInputStream().read()).isEqualTo('8');
        assertThat(wrapped.getInputStream().read()).isEqualTo('9');
        assertThatThrownBy(() -> readAll(wrapped)).isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("10 bytes");
    }

    private static void readAll(ServletRequest request) throws IOException {
        while (request.getInputStream().read() != -1) {
            // drain
        }
    }
}
