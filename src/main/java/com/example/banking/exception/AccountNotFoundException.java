package com.example.banking.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long accountId) {
        super("Account with id %d not found".formatted(accountId));
    }
}
