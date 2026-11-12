package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Transfer limit of an account. Anyone can read it, but only {@link Account} changes it:
 * the mutators are protected, so code outside the model package can't call them.
 * Invariant: {@code used + remaining == limit}; {@link #use(BigDecimal)} keeps both in step.
 */
@Getter
public class Limits {

    private final BigDecimal limit;
    private BigDecimal used;
    private BigDecimal remaining;

    public Limits() {
        this.limit = new BigDecimal("10000.00");
        this.used = MoneyUtils.normalize(BigDecimal.ZERO);
        this.remaining = limit;
    }

    protected boolean allow(BigDecimal amount) {
        return amount.compareTo(remaining) <= 0;
    }

    protected void use(BigDecimal amount) {
        used = used.add(amount);
        remaining = remaining.subtract(amount);
    }
}
