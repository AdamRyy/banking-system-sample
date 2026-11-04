package com.example.banking.dto;

import com.example.banking.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        @JsonProperty("X-Correlation-Id") String correlationId) {

    // correlationId stays null (omitted from JSON) until correlation-id propagation is added.
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, Instant.now(), null);
    }
}
