package com.example.banking.service;

import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final Account acc;

    public AccountService() {
        this(new Account());
    }

    /** Lets tests in this package start from an account with a chosen balance or limit. */
    protected AccountService(Account acc) {
        this.acc = acc;
    }

    public Account getAccount(Long accountId) {
        if (acc.getId().equals(accountId)) {
            return acc;
        }
        throw new AccountNotFoundException(accountId);
    }

    public Limits getLimits(Long accountId) {
        return getAccount(accountId).getLimits();
    }
}
