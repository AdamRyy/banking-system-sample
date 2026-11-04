package com.example.banking.dto;

import com.example.banking.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ofSetsCodeMessageAndTimestampWithoutCorrelationId() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INSUFFICIENT_FUNDS, "no money");

        assertEquals("INSUFFICIENT_FUNDS", response.code());
        assertEquals("no money", response.message());
        assertTrue(response.timestamp() != null);
        assertEquals(null, response.correlationId());
    }

    @Test
    void nullCorrelationIdIsOmittedFromJson() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND, "x"));

        assertFalse(json.contains("X-Correlation-Id"), json);
        assertFalse(json.contains("correlationId"), json);
    }

    @Test
    void correlationIdIsSerializedAsHeaderStyleKey() {
        String json = mapper.writeValueAsString(
                new ErrorResponse("NOT_FOUND", "x", Instant.parse("2026-11-04T08:00:00Z"), "abc-123"));

        assertTrue(json.contains("\"X-Correlation-Id\":\"abc-123\""), json);
        assertFalse(json.contains("correlationId"), json);
    }
}
