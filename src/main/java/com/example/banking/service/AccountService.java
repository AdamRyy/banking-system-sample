package com.example.banking.service;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /** Joins the caller's transaction (e.g. a transfer), so the returned entity stays managed. */
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public Limits getLimits(Long accountId) {
        return getAccount(accountId).getLimits();
    }

    /**
     * Naive on purpose (no JOIN FETCH / @EntityGraph): 1 select for the accounts, then 1 select per
     * account when its lazy transfers are first read. The mapping must stay inside this transaction:
     * open-in-view is off, so touching getTransfers() after it ends throws LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Reads the lazy transfers (1 select), so it must run inside the caller's transaction. */
    private AccountResponse toResponse(Account account) {
        List<TransferHistoryResponse> transfers = account.getTransfers().stream()
                .map(TransferHistoryResponse::from)
                .toList();
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getBalance(), transfers);
    }
}
