package com.example.banking.dto;

import com.example.banking.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ofSetsCodeMessageAndTimestampWithoutCorrelationId() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INSUFFICIENT_FUNDS, "no money");

        assertEquals("INSUFFICIENT_FUNDS", response.code());
        assertEquals("no money", response.message());
        assertNotNull(response.timestamp());
        assertNull(response.correlationId());
    }

    @Test
    void ofCarriesTheGivenCorrelationId() {
        assertEquals("abc-123", ErrorResponse.of(ErrorCode.NOT_FOUND, "x", "abc-123").correlationId());
    }

    @Test
    void ofStatusUsesTheStatusNameAsCode() {
        assertEquals("NOT_ACCEPTABLE", ErrorResponse.of(HttpStatus.NOT_ACCEPTABLE, "x", null).code());
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
