package com.example.banking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitsTest {

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
}
