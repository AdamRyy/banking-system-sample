package com.example.banking.model;

import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    private static Account account(String balance) {
        return new Account(1L, "12345678901234567890123456", new BigDecimal(balance), new Limits());
    }

    private static void assertState(Account account, String balance, String used) {
        // equals() checks scale too, so these also verify 2 decimal places
        assertEquals(new BigDecimal(balance), account.getBalance());
        assertEquals(new BigDecimal(used), account.getLimits().getUsed());
    }

    @Test
    void creditIncreasesBalanceAndLeavesLimitUntouched() {
        Account account = account("1000");

        account.credit(new BigDecimal("100.50"));

        assertState(account, "1100.50", "0.00");
    }

    @Test
    void constructorNormalizesBalance() {
        assertEquals(new BigDecimal("100.00"), account("100").getBalance());
    }

    @Test
    void debitReducesBalanceAndUsesLimit() {
        Account account = account("5000");

        account.debit(new BigDecimal("100.5"));

        assertState(account, "4899.50", "100.50");
    }

    @Test
    void debitAllowsExactBalance() {
        Account account = account("5000");

        account.debit(new BigDecimal("5000"));

        assertState(account, "0.00", "5000.00");
    }

    @Test
    void debitAboveBalanceIsRejectedAndStateUnchanged() {
        Account account = account("500");

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> account.debit(new BigDecimal("500.01")));

        assertEquals("Account 1 has insufficient funds: requested 500.01, available 500.00", ex.getMessage());
        assertState(account, "500.00", "0.00");
    }

    @Test
    void debitAboveRemainingLimitIsRejectedAndStateUnchanged() {
        Account account = account("20000");

        TransferLimitExceededException ex = assertThrows(TransferLimitExceededException.class,
                () -> account.debit(new BigDecimal("10000.01")));

        assertEquals("Account 1 transfer limit exceeded: requested 10000.01, remaining limit 10000.00", ex.getMessage());
        assertState(account, "20000.00", "0.00");
    }

    @Test
    void limitIsCheckedBeforeFunds() {
        // Both rules are broken: the limit violation wins.
        Account account = account("500");

        assertThrows(TransferLimitExceededException.class, () -> account.debit(new BigDecimal("10000.01")));
    }
}
