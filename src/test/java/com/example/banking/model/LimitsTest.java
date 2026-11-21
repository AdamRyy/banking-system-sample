package com.example.banking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitsTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");
    private static final Instant END_OF_DAY = Instant.parse("2026-11-12T23:59:59.999999Z");

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void newLimitsHaveNothingUsed() {
        Limits limits = new Limits();

        assertThat(limits.getLimit()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(limits.getUsed()).isEqualTo(new BigDecimal("0.00"));
        assertThat(limits.getRemaining()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    void useAddsToUsedAndReducesRemaining() {
        Limits limits = new Limits();

        limits.use(new BigDecimal("1500.50"));

        assertThat(limits.getLimit()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(limits.getUsed()).isEqualTo(new BigDecimal("1500.50"));
        assertThat(limits.getRemaining()).isEqualTo(new BigDecimal("8499.50"));
    }

    @Test
    void usedPlusRemainingAlwaysEqualsLimit() {
        Limits limits = new Limits();

        for (String amount : new String[]{"0.01", "2500", "1234.56", "0.99"}) {
            limits.use(new BigDecimal(amount));
            assertThat(limits.getUsed().add(limits.getRemaining()))
                    .as("used %s + remaining %s", limits.getUsed(), limits.getRemaining())
                    .isEqualByComparingTo(limits.getLimit());
        }
    }

    @Test
    void allowsUpToExactlyTheRemainingLimit() {
        Limits limits = new Limits();

        assertThat(limits.allow(new BigDecimal("10000"))).isTrue();
        assertThat(limits.allow(new BigDecimal("10000.01"))).isFalse();

        limits.use(new BigDecimal("9500.00"));
        assertThat(limits.allow(new BigDecimal("500.00"))).isTrue();
        assertThat(limits.allow(new BigDecimal("500.01"))).isFalse();
    }

    @Test
    void expiredWindowResetsUsedAndMovesResetAtToEndOfToday() {
        Limits limits = new Limits(); // resetAt = EPOCH, already expired
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(NOW));

        assertThat(limits.getUsed()).isEqualTo(new BigDecimal("0.00"));
        assertThat(limits.getResetAt()).isEqualTo(END_OF_DAY);
    }

    @Test
    void openWindowKeepsUsed() {
        Limits limits = new Limits();
        limits.resetIfExpired(clockAt(NOW));
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(END_OF_DAY)); // last instant of the window still counts

        assertThat(limits.getUsed()).isEqualTo(new BigDecimal("4000.00"));
        assertThat(limits.getResetAt()).isEqualTo(END_OF_DAY);
    }

    @Test
    void firstInstantOfNextDayResets() {
        Limits limits = new Limits();
        limits.resetIfExpired(clockAt(NOW));
        limits.use(new BigDecimal("4000.00"));

        limits.resetIfExpired(clockAt(Instant.parse("2026-11-13T00:00:00Z")));

        assertThat(limits.getUsed()).isEqualTo(new BigDecimal("0.00"));
        assertThat(limits.getResetAt()).isEqualTo(Instant.parse("2026-11-13T23:59:59.999999Z"));
    }
}
