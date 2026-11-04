package com.example.banking.service;

import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final Account acc = new Account();
    public Account getAccount(Long accountId) {
        if(accountId == 1) {
            return acc;
        }
        throw new AccountNotFoundException(accountId);
    }

    public Limits getLimits(Long accountId) {
        if(accountId == 1) {
            return acc.getLimits();
        }
        throw new AccountNotFoundException(accountId);
    }
}
