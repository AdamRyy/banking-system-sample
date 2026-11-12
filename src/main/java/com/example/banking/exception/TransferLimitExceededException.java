package com.example.banking.exception;

import com.example.banking.util.MoneyUtils;

import java.math.BigDecimal;

public class TransferLimitExceededException extends TransferRejectedException {

    public TransferLimitExceededException(Long accountId, BigDecimal amount, BigDecimal remainingLimit) {
        // Normalized for display only, so every amount reads with 2 decimals ("10000.00", not "10000").
        super("Account %d transfer limit exceeded: requested %s, remaining limit %s"
                .formatted(accountId, MoneyUtils.normalize(amount), MoneyUtils.normalize(remainingLimit)));
    }
}
