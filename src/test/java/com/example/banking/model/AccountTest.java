package com.example.banking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @Test
    void defaultAccountHasHardcodedValuesWithTwoDecimalPlaces() {
        Account account = new Account();
        Limits limits = account.getLimits();

        assertEquals(1L, account.getId());
        // equals() checks scale too, so these also verify 2 decimal places
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
        assertEquals(new BigDecimal("10000.00"), limits.getTransfer());
    }

    @Test
    void setBalanceNormalizesToTwoDecimalPlaces() {
        Account account = new Account();

        account.setBalance(new BigDecimal("100"));

        assertEquals(new BigDecimal("100.00"), account.getBalance());
    }

    @Test
    void hasSufficientFundsAllowsExactBalanceButNotMore() {
        Account account = new Account();

        assertTrue(account.hasSufficientFunds(new BigDecimal("5000")));
        assertFalse(account.hasSufficientFunds(new BigDecimal("5000.01")));
    }
}
