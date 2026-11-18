package com.example.banking.repository;

import com.example.banking.model.Account;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs against H2 built from schema.sql + data.sql; each test rolls back. */
@DataJpaTest
class TransferHistoryIntegrationTest {

    private static final String FIRST = "12345678901234567890123456";
    private static final String SECOND = "65432109876543210987654321";
    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void transferHistoryIsSavedWithItsAccount() {
        Account source = accountRepository.findByAccountNumber(FIRST).orElseThrow();

        Long id = historyRepository.saveAndFlush(source.recordTransfer(SECOND, new BigDecimal("100.5"), NOW, TransferDirection.OUTGOING)).getId();
        entityManager.clear(); // force a real read from H2, not the persistence context

        TransferHistory loaded = historyRepository.findById(id).orElseThrow();
        assertEquals(FIRST, loaded.getAccount().getAccountNumber());
        assertEquals(SECOND, loaded.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.50"), loaded.getAmount());
        assertEquals(NOW, loaded.getTimestamp());
        assertEquals(TransferDirection.OUTGOING, loaded.getDirection());
    }

    @Test
    void findByAccountIdPagesOnlyThatAccountsRowsInTheRequestedOrder() {
        Account source = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Account target = accountRepository.findByAccountNumber(SECOND).orElseThrow();
        historyRepository.saveAll(List.of(
                source.recordTransfer(SECOND, new BigDecimal("10"), NOW, TransferDirection.OUTGOING),
                source.recordTransfer(SECOND, new BigDecimal("30"), NOW.plusSeconds(60), TransferDirection.OUTGOING),
                source.recordTransfer(SECOND, new BigDecimal("20"), NOW.plusSeconds(120), TransferDirection.OUTGOING),
                target.recordTransfer(FIRST, new BigDecimal("10"), NOW, TransferDirection.INCOMING)));
        historyRepository.flush();
        entityManager.clear();

        Page<TransferHistory> first = historyRepository.findByAccountId(source.getId(),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "timestamp")));
        Page<TransferHistory> second = historyRepository.findByAccountId(source.getId(),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(List.of(new BigDecimal("20.00"), new BigDecimal("30.00")),
                first.getContent().stream().map(TransferHistory::getAmount).toList());
        assertEquals(List.of(new BigDecimal("10.00")),
                second.getContent().stream().map(TransferHistory::getAmount).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void transferAmountMustBePositive(String amount) {
        Account source = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        TransferHistory history = source.recordTransfer(SECOND, new BigDecimal(amount),
                NOW, TransferDirection.OUTGOING);

        ConstraintViolationException ex = assertThrows(ConstraintViolationException.class,
                () -> historyRepository.saveAndFlush(history));

        assertEquals("amount", ex.getConstraintViolations().iterator().next().getPropertyPath().toString());
    }
}
