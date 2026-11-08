package com.example.banking.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The headers every request needs to get past RequestHeadersInterceptor. */
final class TestHeaders {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    static final String CORRELATION_ID = "test-correlation-id";
    static final String VALID_AUTHORIZATION = "Bearer token-123";

    private TestHeaders() {
    }

    static MockHttpServletRequestBuilder withValidHeaders(MockHttpServletRequestBuilder request) {
        return request
                .header(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION)
                .header(CORRELATION_ID_HEADER, CORRELATION_ID);
    }
}
