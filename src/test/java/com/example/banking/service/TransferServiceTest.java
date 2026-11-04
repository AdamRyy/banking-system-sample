package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferServiceTest {

    private TransferService transferService;
    private Account source;

    @BeforeEach
    void setUp() {
        AccountService accountService = new AccountService();
        transferService = new TransferService(accountService);
        source = accountService.getAccount(1L);
    }

    private static TransferRequest request(String amount) {
        return new TransferRequest(1L, 2L, new BigDecimal(amount));
    }

    private void assertState(String balance, String limit) {
        assertEquals(new BigDecimal(balance), source.getBalance());
        assertEquals(new BigDecimal(limit), source.getLimits().getTransfer());
    }

    @Test
    void successfulTransferReducesBalanceAndLimit() {
        TransferResponse response = transferService.transfer(request("100.5"));

        assertEquals(1L, response.sourceId());
        assertEquals(2L, response.targetId());
        assertEquals(new BigDecimal("100.50"), response.amount());
        assertEquals(new BigDecimal("4899.50"), response.accBalance());
        assertEquals(new BigDecimal("9899.50"), response.remainingLimit());
        assertState("4899.50", "9899.50");
    }

    @Test
    void amountAboveRemainingLimitIsRejectedAndStateUnchanged() {
        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("10000.01")));

        assertState("5000.00", "10000.00");
    }

    @Test
    void amountAboveBalanceIsRejectedAndStateUnchanged() {
        source.setBalance(new BigDecimal("500"));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> transferService.transfer(request("600")));

        assertEquals("Account 1 has insufficient funds: requested 600.00, available 500.00", ex.getMessage());

        assertState("500.00", "10000.00");
    }

    @Test
    void checksUseNormalizedAmount() {
        source.setBalance(new BigDecimal("20000"));

        // 10000.001 rounds to 10000.00, which is exactly the limit -> allowed
        transferService.transfer(request("10000.001"));

        assertState("10000.00", "0.00");
    }

    @Test
    void unknownSourceAccountIsRejected() {
        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(new TransferRequest(2L, 1L, new BigDecimal("10"))));
    }

    @Test
    void consecutiveTransfersAccumulateAgainstLimit() {
        source.setBalance(new BigDecimal("20000"));

        transferService.transfer(request("3000"));
        transferService.transfer(request("3000"));
        assertState("14000.00", "4000.00");

        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("5000")));
        assertState("14000.00", "4000.00");
    }
}
