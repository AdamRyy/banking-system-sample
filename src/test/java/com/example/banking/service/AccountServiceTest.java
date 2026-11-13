package com.example.banking.service;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Unit test: the repository is mocked; PersistenceTest covers real H2 access. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private final Account account =
            new Account(1L, "12345678901234567890123456", new BigDecimal("5000.00"), new Limits());

    @Test
    void getAccountReturnsAccountFromRepository() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertSame(account, accountService.getAccount(1L));
    }

    @Test
    void getLimitsReturnsLimitsForExistingAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertEquals(new BigDecimal("10000.00"), accountService.getLimits(1L).getLimit());
    }

    @Test
    void getLimitsThrowsForUnknownAccount() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class, () -> accountService.getLimits(99L));

        assertEquals("Account with id 99 not found", ex.getMessage());
    }

    @Test
    void getAllAccountsMapsAccountsWithTheirTransfers() {
        Account other = new Account(2L, "65432109876543210987654321", new BigDecimal("1000.00"), new Limits());
        Instant now = Instant.parse("2026-11-12T10:15:30Z");
        new TransferHistory(account, other.getAccountNumber(), new BigDecimal("100"), now, TransferDirection.OUTGOING);
        new TransferHistory(other, account.getAccountNumber(), new BigDecimal("100"), now, TransferDirection.INCOMING);
        when(accountRepository.findAll()).thenReturn(List.of(account, other));

        List<AccountResponse> result = accountService.getAllAccounts();

        assertEquals(2, result.size());
        AccountResponse first = result.get(0);
        assertEquals(1L, first.id());
        assertEquals("12345678901234567890123456", first.accountNumber());
        assertEquals(new BigDecimal("5000.00"), first.balance());
        TransferHistoryResponse outgoing = first.transfers().getFirst();
        assertEquals(TransferDirection.OUTGOING, outgoing.direction());
        assertEquals("65432109876543210987654321", outgoing.counterpartyAccountNumber());
        assertEquals(new BigDecimal("100.00"), outgoing.amount());
        assertEquals(now, outgoing.timestamp());

        TransferHistoryResponse incoming = result.get(1).transfers().getFirst();
        assertEquals(TransferDirection.INCOMING, incoming.direction());
        assertEquals("12345678901234567890123456", incoming.counterpartyAccountNumber());
    }

    @Test
    void getAllAccountsReturnsEmptyListWhenThereAreNoAccounts() {
        when(accountRepository.findAll()).thenReturn(List.of());

        assertTrue(accountService.getAllAccounts().isEmpty());
    }

    @Test
    void getAllAccountsReturnsEmptyTransfersForAccountWithoutHistory() {
        when(accountRepository.findAll()).thenReturn(List.of(account));

        List<AccountResponse> result = accountService.getAllAccounts();

        assertEquals(1, result.size());
        assertEquals(List.of(), result.getFirst().transfers());   // empty list, never null
    }
}
