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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit test: the repository is mocked; the *IntegrationTest classes cover real database access. */
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

        assertThat(accountService.getAccount(1L)).isSameAs(account);
    }

    @Test
    void getLimitsReturnsLimitsForExistingAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThat(accountService.getLimits(1L).getLimit()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    void getLimitsThrowsForUnknownAccount() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getLimits(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account with id 99 not found");
    }
}
