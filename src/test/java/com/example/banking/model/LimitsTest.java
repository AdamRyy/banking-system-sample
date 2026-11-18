package com.example.banking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitsTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");
    private static final Instant END_OF_DAY = Instant.parse("2026-11-12T23:59:59.999999Z");

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void newLimitsHaveNothingUsed() {
        Limits limits = new Limits();

        assertEquals(new BigDecimal("10000.00"), limits.getLimit());
        assertEquals(new BigDecimal("0.00"), limits.getUsed());
        assertEquals(new BigDecimal("10000.00"), limits.getRemaining());
    }

    @Test
    void useAddsToUsedAndReducesRemaining() {
        Limits limits = new Limits();

        limits.use(new BigDecimal("1500.50"));

        assertEquals(new BigDecimal("10000.00"), limits.getLimit());
        assertEquals(new BigDecimal("1500.50"), limits.getUsed());
        assertEquals(new BigDecimal("8499.50"), limits.getRemaining());
    }

    @Test
    void usedPlusRemainingAlwaysEqualsLimit() {
        Limits limits = new Limits();

        for (String amount : new String[]{"0.01", "2500", "1234.56", "0.99"}) {
            limits.use(new BigDecimal(amount));
            assertEquals(0, limits.getUsed().add(limits.getRemaining()).compareTo(limits.getLimit()),
                    "used " + limits.getUsed() + " + remaining " + limits.getRemaining());
        }
    }

    @Test
    void allowsUpToExactlyTheRemainingLimit() {
        Limits limits = new Limits();

        assertTrue(limits.allow(new BigDecimal("10000")));
        assertFalse(limits.allow(new BigDecimal("10000.01")));

        limits.use(new BigDecimal("9500.00"));
        assertTrue(limits.allow(new BigDecimal("500.00")));
        assertFalse(limits.allow(new BigDecimal("500.01")));
    }

    @Test
    void expiredWindowResetsUsedAndMovesResetAtToEndOfToday() {
        Limits limits = new Limits(); // resetAt = EPOCH, already expired
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(NOW));

        assertEquals(new BigDecimal("0.00"), limits.getUsed());
        assertEquals(END_OF_DAY, limits.getResetAt());
    }

    @Test
    void openWindowKeepsUsed() {
        Limits limits = new Limits();
        limits.resetIfExpired(clockAt(NOW));
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(END_OF_DAY)); // last instant of the window still counts

        assertEquals(new BigDecimal("4000.00"), limits.getUsed());
        assertEquals(END_OF_DAY, limits.getResetAt());
    }

    @Test
    void firstInstantOfNextDayResets() {
        Limits limits = new Limits();
        limits.resetIfExpired(clockAt(NOW));
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(Instant.parse("2026-11-13T00:00:00Z")));

        assertEquals(new BigDecimal("0.00"), limits.getUsed());
        assertEquals(Instant.parse("2026-11-13T23:59:59.999999Z"), limits.getResetAt());
    }
}
