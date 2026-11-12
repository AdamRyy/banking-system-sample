package com.example.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's single source of "now". Inject it and call {@code Instant.now(clock)} instead of
 * {@code Instant.now()}, so tests can swap in a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
