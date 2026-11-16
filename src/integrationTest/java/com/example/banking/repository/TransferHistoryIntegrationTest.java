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

import java.math.BigDecimal;
import java.time.Instant;

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
