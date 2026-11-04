package com.example.banking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitsTest {

    @Test
    void subtractTransferReducesRemainingLimit() {
        Limits limits = new Limits();

        limits.subtractTransfer(new BigDecimal("1500.50"));

        assertEquals(new BigDecimal("8499.50"), limits.getTransfer());
    }

    @Test
    void subtractTransferKeepsTwoDecimalPlaces() {
        Limits limits = new Limits();

        limits.subtractTransfer(new BigDecimal("0.005"));

        assertEquals(new BigDecimal("10000.00"), limits.getTransfer());
    }

    @Test
    void isTransferLimitExceededComparesAgainstRemainingLimit() {
        Limits limits = new Limits();

        assertFalse(limits.isTransferLimitExceeded(new BigDecimal("10000")));
        assertTrue(limits.isTransferLimitExceeded(new BigDecimal("10000.01")));

        limits.subtractTransfer(new BigDecimal("9500.00"));
        assertTrue(limits.isTransferLimitExceeded(new BigDecimal("600.00")));
    }
}
