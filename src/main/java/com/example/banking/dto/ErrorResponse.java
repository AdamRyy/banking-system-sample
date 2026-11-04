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

    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code, message, null);
    }

    /** A null correlationId is omitted from the JSON. */
    public static ErrorResponse of(ErrorCode code, String message, String correlationId) {
        return new ErrorResponse(code.name(), message, Instant.now(), correlationId);
    }

    /** For errors with no dedicated ErrorCode: the status name is used as the code. */
    public static ErrorResponse of(HttpStatus status, String message, String correlationId) {
        return new ErrorResponse(status.name(), message, Instant.now(), correlationId);
    }

    /** For errors raised outside the application: only the status is known, and no details are exposed. */
    public static ErrorResponse forStatus(HttpStatus status, String correlationId) {
        return status.is5xxServerError()
                ? of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", correlationId)
                : of(status, status.getReasonPhrase(), correlationId);
    }
}
