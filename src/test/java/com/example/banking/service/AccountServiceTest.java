package com.example.banking.service;

import com.example.banking.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountServiceTest {

    private final AccountService accountService = new AccountService();

    @Test
    void getLimitsReturnsLimitsForExistingAccount() {
        assertEquals(new BigDecimal("10000.00"), accountService.getLimits(1L).getTransfer());
    }

    @Test
    void getLimitsThrowsForUnknownAccount() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getLimits(2L));
    }
}
