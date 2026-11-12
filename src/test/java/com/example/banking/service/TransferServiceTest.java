package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferServiceTest {

    private Account source;
    private TransferService transferService;

    /** Uses the protected AccountService constructor to start from a chosen balance. */
    private void givenBalance(String balance) {
        source = new Account(1L, new BigDecimal(balance), new Limits());
        transferService = new TransferService(new AccountService(source));
    }

    private static TransferRequest request(String amount) {
        return new TransferRequest(1L, 2L, new BigDecimal(amount));
    }

    private void assertState(String balance, String remainingLimit) {
        assertEquals(new BigDecimal(balance), source.getBalance());
        assertEquals(new BigDecimal(remainingLimit), source.getLimits().getRemaining());
    }

    @Test
    void successfulTransferReducesBalanceAndLimit() {
        givenBalance("5000");

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
        givenBalance("5000");

        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("10000.01")));

        assertState("5000.00", "10000.00");
    }

    @Test
    void amountAboveBalanceIsRejectedAndStateUnchanged() {
        givenBalance("500");

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> transferService.transfer(request("600")));   // formatted as 600.00 in the message

        assertEquals("Account 1 has insufficient funds: requested 600.00, available 500.00", ex.getMessage());
        assertState("500.00", "10000.00");
    }

    @Test
    void unknownSourceAccountIsRejected() {
        givenBalance("5000");

        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(new TransferRequest(2L, 1L, new BigDecimal("10"))));
    }

    @Test
    void consecutiveTransfersAccumulateAgainstLimit() {
        givenBalance("20000");

        transferService.transfer(request("3000"));
        transferService.transfer(request("3000"));
        assertState("14000.00", "4000.00");

        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("5000")));
        assertState("14000.00", "4000.00");
    }
}
