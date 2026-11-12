package com.example.banking.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Stops time for @WebMvcTest classes, so error responses carry a known timestamp.
 * Needed by every web-slice test: GlobalExceptionHandler requires a Clock, and the slice doesn't load ClockConfig.
 */
@TestConfiguration
public class FixedClockConfig {

    /** The timestamp every error response has in tests, as Jackson serializes it. */
    public static final String NOW = "2026-11-04T08:00:00Z";

    @Bean
    public Clock clock() {
        return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
    }
}
