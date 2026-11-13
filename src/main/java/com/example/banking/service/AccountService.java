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

    /** One select: the entity graph fetches every account's transfers in the same query (no N+1). */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAllWithTransfers().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Reads the lazy transfers: free after findAllWithTransfers(), otherwise 1 hidden select per account.
     * Must run inside a transaction (open-in-view is off), or getTransfers() throws LazyInitializationException.
     */
    private AccountResponse toResponse(Account account) {
        List<TransferHistoryResponse> transfers = account.getTransfers().stream()
                .map(TransferHistoryResponse::from)
                .toList();
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getBalance(), transfers);
    }
}
