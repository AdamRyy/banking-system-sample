package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Daily transfer limit. Changed only through {@link Account}. */
@Embeddable
@Getter
public class Limits {

    @Column(name = "daily_limit_transfer", precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal limit;

    @Column(name = "daily_limit_used", precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal used;

    /** Last instant of the current daily window; after it, {@link #used} starts again from 0. */
    @NotNull
    @Column(name = "daily_limit_reset_at", nullable = false)
    private Instant resetAt;

    /** Default limit; also the no-arg constructor JPA needs (it overwrites the fields on load). */
    public Limits() {
        this.limit = new BigDecimal("10000.00");
        this.used = MoneyUtils.normalize(BigDecimal.ZERO);
        this.resetAt = Instant.EPOCH;
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

    protected void resetIfExpired(Clock clock) {
        Instant now = clock.instant();
        if (resetAt.isBefore(now)) {
            used = MoneyUtils.normalize(BigDecimal.ZERO);
            resetAt = endOfDay(now, clock.getZone());
        }
    }

    /** Truncated to micros: the column is TIMESTAMP(6), and H2 would round 23:59:59.999999999 up to the next day. */
    private static Instant endOfDay(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).atTime(LocalTime.MAX).atZone(zone).toInstant()
                .truncatedTo(ChronoUnit.MICROS);
    }
}
