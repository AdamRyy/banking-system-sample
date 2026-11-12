package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransferHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test of the transfer logic on in-memory accounts. Whether the changes are actually
 * committed (or rolled back) is TransferIntegrationTest's job: with a mocked repository
 * there is no transaction to test.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferHistoryRepository transferHistoryRepository;

    private TransferService transferService;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                new AccountService(accountRepository), transferHistoryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Dirty checking persists account changes, so the service must never save() an account explicitly. */
    @AfterEach
    void noExplicitSave() {
        verify(accountRepository, never()).save(any());
    }

    private void givenAccounts(String sourceBalance) {
        source = new Account(1L, "12345678901234567890123456", new BigDecimal(sourceBalance), new Limits());
        target = new Account(2L, "65432109876543210987654321", new BigDecimal("1000"), new Limits());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
    }

    private static TransferRequest request(String amount) {
        return new TransferRequest(1L, 2L, new BigDecimal(amount));
    }

    private void assertSource(String balance, String remainingLimit) {
        assertEquals(new BigDecimal(balance), source.getBalance());
        assertEquals(new BigDecimal(remainingLimit), source.getLimits().getRemaining());
    }

    @Test
    void successfulTransferReducesBalanceAndLimit() {
        givenAccounts("5000");

        TransferResponse response = transferService.transfer(request("100.5"));

        assertEquals(1L, response.sourceId());
        assertEquals(2L, response.targetId());
        assertEquals(new BigDecimal("100.50"), response.amount());
        assertEquals(new BigDecimal("4899.50"), response.accBalance());
        assertEquals(new BigDecimal("9899.50"), response.remainingLimit());
        assertSource("4899.50", "9899.50");
    }

    @Test
    void successfulTransferCreditsTargetWithoutUsingItsLimit() {
        givenAccounts("5000");

        transferService.transfer(request("100.5"));

        assertEquals(new BigDecimal("1100.50"), target.getBalance());
        assertEquals(new BigDecimal("10000.00"), target.getLimits().getRemaining());
    }

    @Test
    void successfulTransferRecordsHistoryForSource() {
        givenAccounts("5000");

        transferService.transfer(request("100.5"));

        ArgumentCaptor<TransferHistory> captor = ArgumentCaptor.forClass(TransferHistory.class);
        verify(transferHistoryRepository).save(captor.capture());   // exactly once
        TransferHistory history = captor.getValue();
        assertSame(source, history.getAccount());
        assertEquals(target.getAccountNumber(), history.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.50"), history.getAmount());
        assertEquals(NOW, history.getTimestamp());
    }

    @Test
    void amountAboveRemainingLimitIsRejectedAndStateUnchanged() {
        givenAccounts("5000");

        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("10000.01")));

        assertSource("5000.00", "10000.00");
        assertEquals(new BigDecimal("1000.00"), target.getBalance());
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void amountAboveBalanceIsRejectedAndStateUnchanged() {
        givenAccounts("500");

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> transferService.transfer(request("600")));   // formatted as 600.00 in the message

        assertEquals("Account 1 has insufficient funds: requested 600.00, available 500.00", ex.getMessage());
        assertSource("500.00", "10000.00");
        assertEquals(new BigDecimal("1000.00"), target.getBalance());
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void unknownSourceAccountIsRejected() {
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(new TransferRequest(3L, 1L, new BigDecimal("10"))));
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void unknownTargetAccountIsRejectedBeforeSourceIsDebited() {
        source = new Account(1L, "12345678901234567890123456", new BigDecimal("5000"), new Limits());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(new TransferRequest(1L, 3L, new BigDecimal("10"))));

        assertEquals("Account with id 3 not found", ex.getMessage());
        assertSource("5000.00", "10000.00");
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void consecutiveTransfersAccumulateAgainstLimit() {
        givenAccounts("20000");

        transferService.transfer(request("3000"));
        transferService.transfer(request("3000"));
        assertSource("14000.00", "4000.00");

        assertThrows(TransferLimitExceededException.class, () -> transferService.transfer(request("5000")));
        assertSource("14000.00", "4000.00");
        assertEquals(new BigDecimal("7000.00"), target.getBalance());
        verify(transferHistoryRepository, times(2)).save(any());   // the rejected third transfer adds none
    }
}
