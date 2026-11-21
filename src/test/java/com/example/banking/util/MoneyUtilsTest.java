package com.example.banking.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyUtilsTest {

    @Test
    void nullBecomesZeroWithTwoDecimalPlaces() {
        assertThat(MoneyUtils.normalize(null)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void addsMissingDecimalPlaces() {
        assertThat(MoneyUtils.normalize(new BigDecimal("100"))).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void roundsExtraDecimalPlacesHalfEven() {
        assertThat(MoneyUtils.normalize(new BigDecimal("9999.995"))).isEqualTo(new BigDecimal("10000.00"));
        assertThat(MoneyUtils.normalize(new BigDecimal("0.125"))).isEqualTo(new BigDecimal("0.12"));
    }
}
