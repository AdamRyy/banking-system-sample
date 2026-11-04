package com.example.banking.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyUtilsTest {

    @Test
    void nullBecomesZeroWithTwoDecimalPlaces() {
        assertEquals(new BigDecimal("0.00"), MoneyUtils.normalize(null));
    }

    @Test
    void addsMissingDecimalPlaces() {
        assertEquals(new BigDecimal("100.00"), MoneyUtils.normalize(new BigDecimal("100")));
    }

    @Test
    void roundsExtraDecimalPlacesHalfEven() {
        assertEquals(new BigDecimal("10000.00"), MoneyUtils.normalize(new BigDecimal("9999.995")));
        assertEquals(new BigDecimal("0.12"), MoneyUtils.normalize(new BigDecimal("0.125")));
    }
}
