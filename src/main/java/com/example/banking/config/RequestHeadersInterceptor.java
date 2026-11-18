package com.example.banking.config;

import com.example.banking.exception.MissingCorrelationIdException;
import com.example.banking.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Runs before every controller and rejects requests without the required headers.
 * Exceptions thrown here are rendered by GlobalExceptionHandler, like those from a controller.
 */
public class RequestHeadersInterceptor implements HandlerInterceptor {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;

    public RequestHeadersInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    /** The request's correlation id, or null if the header is missing or blank. */
    public static String correlationIdOf(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_ID);
        return StringUtils.hasText(value) ? value : null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if(!StringUtils.hasText(authorization) || !isTokenValid(authorization)) {
            throw new UnauthorizedException();
        }

        String correlationId = correlationIdOf(request);
        if (correlationId != null) {
            response.setHeader(CORRELATION_ID, correlationId);
        }

        if(!StringUtils.hasText(correlationId)) {
            throw new MissingCorrelationIdException();
        }

        return true;
    }

    private boolean isTokenValid(String authorization) {
        return MessageDigest.isEqual(
                (BEARER_PREFIX + expectedToken).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8));
    }
}
