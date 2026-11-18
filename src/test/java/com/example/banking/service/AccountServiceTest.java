package com.example.banking.service;

import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
