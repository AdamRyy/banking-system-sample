package com.example.banking.dto;

import com.example.banking.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ofSetsCodeMessageAndTimestampWithoutCorrelationId() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INSUFFICIENT_FUNDS, "no money", null, NOW);

        assertThat(response.code()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.message()).isEqualTo("no money");
        assertThat(response.timestamp()).isEqualTo(NOW);
        assertThat(response.correlationId()).isNull();
    }

    @Test
    void ofCarriesTheGivenCorrelationId() {
        assertThat(ErrorResponse.of(ErrorCode.NOT_FOUND, "x", "abc-123", NOW).correlationId()).isEqualTo("abc-123");
    }

    @Test
    void ofStatusUsesTheStatusNameAsCode() {
        assertThat(ErrorResponse.of(HttpStatus.NOT_ACCEPTABLE, "x", null, NOW).code()).isEqualTo("NOT_ACCEPTABLE");
    }

    @Test
    void forStatusHidesDetailsOfServerErrors() {
        ErrorResponse response = ErrorResponse.forStatus(HttpStatus.SERVICE_UNAVAILABLE, null, NOW);

        assertThat(response.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.message()).isEqualTo("An unexpected error occurred");
        assertThat(response.timestamp()).isEqualTo(NOW);
    }

    @Test
    void timestampIsSerializedAsIsoUtc() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND, "x", null, NOW));

        assertThat(json).contains("\"timestamp\":\"2026-11-04T08:00:00Z\"");
    }

    @Test
    void nullCorrelationIdIsOmittedFromJson() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND, "x", null, NOW));

        assertThat(json).doesNotContain("X-Correlation-Id", "correlationId");
    }

    @Test
    void correlationIdIsSerializedAsHeaderStyleKey() {
        String json = mapper.writeValueAsString(new ErrorResponse("NOT_FOUND", "x", NOW, "abc-123"));

        assertThat(json)
                .contains("\"X-Correlation-Id\":\"abc-123\"")
                .doesNotContain("correlationId");
    }
}
