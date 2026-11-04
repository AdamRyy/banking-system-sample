package com.example.banking.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends TransferRejectedException {

    public InsufficientFundsException(Long accountId, BigDecimal amount, BigDecimal balance) {
        super("Account %d has insufficient funds: requested %s, available %s"
                .formatted(accountId, amount, balance));
    }
}
