package com.example.banking.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyUtils() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(SCALE, ROUNDING);
    }

}
