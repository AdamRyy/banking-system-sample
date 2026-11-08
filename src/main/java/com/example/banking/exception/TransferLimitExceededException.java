package com.example.banking.exception;

import java.math.BigDecimal;

public class TransferLimitExceededException extends TransferRejectedException {

    public TransferLimitExceededException(Long accountId, BigDecimal amount, BigDecimal remainingLimit) {
        super("Account %d transfer limit exceeded: requested %s, remaining limit %s"
                .formatted(accountId, amount, remainingLimit));
    }
}
