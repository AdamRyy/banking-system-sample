package com.example.banking.dto;

import com.example.banking.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        @JsonProperty("X-Correlation-Id") String correlationId) {

    // The timestamp is passed in rather than read here, so callers can take it from an injected Clock.

    /** A null correlationId is omitted from the JSON. */
    public static ErrorResponse of(ErrorCode code, String message, String correlationId, Instant timestamp) {
        return new ErrorResponse(code.name(), message, timestamp, correlationId);
    }

    /** For errors with no dedicated ErrorCode: the status name is used as the code. */
    public static ErrorResponse of(HttpStatus status, String message, String correlationId, Instant timestamp) {
        return new ErrorResponse(status.name(), message, timestamp, correlationId);
    }

    /** For errors raised outside the application: only the status is known, and no details are exposed. */
    public static ErrorResponse forStatus(HttpStatus status, String correlationId, Instant timestamp) {
        return status.is5xxServerError()
                ? of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", correlationId, timestamp)
                : of(status, status.getReasonPhrase(), correlationId, timestamp);
    }
}
