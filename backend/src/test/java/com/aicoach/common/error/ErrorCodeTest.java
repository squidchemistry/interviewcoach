package com.aicoach.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
            "400, MALFORMED_REQUEST", "401, UNAUTHENTICATED", "403, FORBIDDEN", "404, NOT_FOUND",
            "405, METHOD_NOT_ALLOWED", "406, NOT_ACCEPTABLE", "413, PAYLOAD_TOO_LARGE",
            "415, UNSUPPORTED_MEDIA_TYPE", "503, SERVICE_UNAVAILABLE", "500, INTERNAL", "502, INTERNAL",
            "409, CONFLICT", "429, RATE_LIMITED", "418, MALFORMED_REQUEST"})
    void mapsHttpStatusToCode(int status, ErrorCode expected) {
        assertThat(ErrorCode.fromStatus(status)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PAYLOAD_TOO_LARGE, https://aicoach.dev/errors/payload-too-large",
            "NOT_FOUND, https://aicoach.dev/errors/not-found"})
    void typeUriIsKebabCase(ErrorCode code, String uri) {
        assertThat(code.typeUri()).isEqualTo(uri);
    }
}
