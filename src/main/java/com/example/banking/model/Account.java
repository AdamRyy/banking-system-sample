package com.example.banking.model;

import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.util.MoneyUtils;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Read access is public. State changes only through {@link #debit(BigDecimal)}, which keeps
 * the balance and the used limit consistent.
 */
@Getter
public class Account {

    private final Long id;
    private BigDecimal balance;
    private final Limits limits;

    public Account() {
        this(1L, new BigDecimal("5000.00"), new Limits());
    }

    public Account(Long id, BigDecimal balance, Limits limits) {
        this.id = id;
        this.balance = MoneyUtils.normalize(balance);
        this.limits = limits;
    }

    public void debit(BigDecimal amount) {
        if (!limits.allow(amount)) {
            throw new TransferLimitExceededException(id, amount, limits.getRemaining());
        }
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(id, amount, balance);
        }
        balance = balance.subtract(amount);
        limits.use(amount);
    }
}
