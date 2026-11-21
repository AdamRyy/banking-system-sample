package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.event.TransferCompletedEvent;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferService transferService;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(new AccountService(accountRepository), transferHistoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), eventPublisher);
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
        assertThat(source.getBalance()).isEqualTo(new BigDecimal(balance));
        assertThat(source.getLimits().getRemaining()).isEqualTo(new BigDecimal(remainingLimit));
    }

    @Test
    void successfulTransferReducesBalanceAndLimit() {
        givenAccounts("5000");

        TransferResponse response = transferService.transfer(request("100.5"));

        assertThat(response.sourceId()).isEqualTo(1L);
        assertThat(response.targetId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(response.accBalance()).isEqualTo(new BigDecimal("4899.50"));
        assertThat(response.remainingLimit()).isEqualTo(new BigDecimal("9899.50"));
        assertSource("4899.50", "9899.50");
    }

    @Test
    void successfulTransferCreditsTargetWithoutUsingItsLimit() {
        givenAccounts("5000");

        transferService.transfer(request("100.5"));

        assertThat(target.getBalance()).isEqualTo(new BigDecimal("1100.50"));
        assertThat(target.getLimits().getRemaining()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulTransferRecordsOutgoingForSourceAndIncomingForTarget() {
        givenAccounts("5000");

        transferService.transfer(request("100.5"));

        ArgumentCaptor<List<TransferHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferHistoryRepository).saveAll(captor.capture());   // exactly once
        List<TransferHistory> rows = captor.getValue();
        assertThat(rows).hasSize(2);

        TransferHistory outgoing = rows.getFirst();
        assertThat(outgoing.getAccount()).isSameAs(source);
        assertThat(outgoing.getDirection()).isEqualTo(TransferDirection.OUTGOING);
        assertThat(outgoing.getCounterpartyAccountNumber()).isEqualTo(target.getAccountNumber());
        assertThat(outgoing.getAmount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(outgoing.getTimestamp()).isEqualTo(NOW);

        TransferHistory incoming = rows.get(1);
        assertThat(incoming.getAccount()).isSameAs(target);
        assertThat(incoming.getDirection()).isEqualTo(TransferDirection.INCOMING);
        assertThat(incoming.getCounterpartyAccountNumber()).isEqualTo(source.getAccountNumber());
        assertThat(incoming.getAmount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(incoming.getTimestamp()).isEqualTo(NOW);
    }

    @Test
    void successfulTransferPublishesTransferCompletedEvent() {
        givenAccounts("5000");
        // Stand in for the IDENTITY insert, which sets the ids during saveAll.
        when(transferHistoryRepository.saveAll(any())).thenAnswer(invocation -> {
            List<TransferHistory> rows = invocation.getArgument(0);
            for (TransferHistory row : rows) {
                ReflectionTestUtils.setField(row, "id", row.getDirection() == TransferDirection.OUTGOING ? 41L : 42L);
            }
            return rows;
        });

        transferService.transfer(request("100.5"));

        ArgumentCaptor<TransferCompletedEvent> captor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());   // exactly once
        TransferCompletedEvent event = captor.getValue();
        assertThat(event.transferId()).isEqualTo(41L);   // the OUTGOING row's id
        assertThat(event.sourceAccountNumber()).isEqualTo(source.getAccountNumber());
        assertThat(event.targetAccountNumber()).isEqualTo(target.getAccountNumber());
        assertThat(event.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    void amountAboveRemainingLimitIsRejectedAndStateUnchanged() {
        givenAccounts("5000");

        assertThatThrownBy(() -> transferService.transfer(request("10000.01")))
                .isInstanceOf(TransferLimitExceededException.class);

        assertSource("5000.00", "10000.00");
        assertThat(target.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        verifyNoInteractions(transferHistoryRepository, eventPublisher);
    }

    @Test
    void amountAboveBalanceIsRejectedAndStateUnchanged() {
        givenAccounts("500");

        assertThatThrownBy(() -> transferService.transfer(request("600")))   // formatted as 600.00 in the message
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account 1 has insufficient funds: requested 600.00, available 500.00");
        assertSource("500.00", "10000.00");
        assertThat(target.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        verifyNoInteractions(transferHistoryRepository, eventPublisher);
    }

    @Test
    void unknownSourceAccountIsRejected() {
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(3L, 1L, new BigDecimal("10"))))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(transferHistoryRepository, eventPublisher);
    }

    @Test
    void unknownTargetAccountIsRejectedBeforeSourceIsDebited() {
        source = new Account(1L, "12345678901234567890123456", new BigDecimal("5000"), new Limits());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(1L, 3L, new BigDecimal("10"))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account with id 3 not found");
        assertSource("5000.00", "10000.00");
        verifyNoInteractions(transferHistoryRepository, eventPublisher);
    }

    @Test
    void consecutiveTransfersAccumulateAgainstLimit() {
        givenAccounts("20000");

        transferService.transfer(request("3000"));
        transferService.transfer(request("3000"));
        assertSource("14000.00", "4000.00");

        assertThatThrownBy(() -> transferService.transfer(request("5000")))
                .isInstanceOf(TransferLimitExceededException.class);
        assertSource("14000.00", "4000.00");
        assertThat(target.getBalance()).isEqualTo(new BigDecimal("7000.00"));
        verify(transferHistoryRepository, times(2)).saveAll(any());   // the rejected third transfer adds none
    }

    @Test
    void limitUsedYesterdayIsAvailableAgainToday() {
        givenAccounts("20000");
        TransferService yesterday = new TransferService(new AccountService(accountRepository),
                transferHistoryRepository, Clock.fixed(NOW.minus(1, ChronoUnit.DAYS), ZoneOffset.UTC), eventPublisher);
        yesterday.transfer(request("9000"));
        assertSource("11000.00", "1000.00");

        TransferResponse response = transferService.transfer(request("5000"));

        assertThat(response.remainingLimit()).isEqualTo(new BigDecimal("5000.00"));
        assertSource("6000.00", "5000.00");
    }
}
