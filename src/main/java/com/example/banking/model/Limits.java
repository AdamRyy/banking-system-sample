package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.math.BigDecimal;

@Embeddable
@Getter
public class Limits {

    // "limit" is a reserved word in H2, hence the column names.
    @Column(name = "transfer_limit", precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal limit;

    @Column(name = "limit_used", precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal used;

    /** Default limit; also the no-arg constructor JPA needs (it overwrites the fields on load). */
    public Limits() {
        this.limit = new BigDecimal("10000.00");
        this.used = MoneyUtils.normalize(BigDecimal.ZERO);
    }

    public BigDecimal getRemaining() {
        return limit.subtract(used);
    }

    protected boolean allow(BigDecimal amount) {
        return amount.compareTo(getRemaining()) <= 0;
    }

    protected void use(BigDecimal amount) {
        used = used.add(amount);
    }
}
