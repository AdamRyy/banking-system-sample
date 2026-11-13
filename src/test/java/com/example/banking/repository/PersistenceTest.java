package com.example.banking.repository;

import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against H2 built from schema.sql + data.sql; each test rolls back. */
@DataJpaTest
class PersistenceTest {

    private static final String FIRST = "12345678901234567890123456";
    private static final String SECOND = "65432109876543210987654321";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void seedDataLoadsTwoAccounts() {
        List<Account> accounts = accountRepository.findAll();

        assertEquals(2, accounts.size());
        Account first = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Account second = accountRepository.findByAccountNumber(SECOND).orElseThrow();
        assertEquals(new BigDecimal("5000.00"), first.getBalance());
        assertEquals(new BigDecimal("1000.00"), second.getBalance());
        assertEquals(new BigDecimal("10000.00"), first.getLimits().getRemaining());
    }

    @Test
    void transferHistoryIsSavedWithItsAccount() {
        Account source = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Instant now = Instant.parse("2026-11-12T10:15:30Z");

        Long id = historyRepository.saveAndFlush(new TransferHistory(source, SECOND, new BigDecimal("100.5"), now, TransferDirection.OUTGOING)).getId();
        entityManager.clear(); // force a real read from H2, not the persistence context

        TransferHistory loaded = historyRepository.findById(id).orElseThrow();
        assertEquals(FIRST, loaded.getAccount().getAccountNumber());
        assertEquals(SECOND, loaded.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.50"), loaded.getAmount());
        assertEquals(now, loaded.getTimestamp());
        assertEquals(TransferDirection.OUTGOING, loaded.getDirection());
    }

    @Test
    void accountTransfersAreLoadedLazilyFromHistory() {
        Account source = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Instant now = Instant.parse("2026-11-12T10:15:30Z");
        historyRepository.saveAndFlush(
                new TransferHistory(source, SECOND, new BigDecimal("10"), now, TransferDirection.OUTGOING));
        entityManager.clear();

        Account reloaded = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Account other = accountRepository.findByAccountNumber(SECOND).orElseThrow();

        assertEquals(1, reloaded.getTransfers().size());
        assertEquals(SECOND, reloaded.getTransfers().getFirst().getCounterpartyAccountNumber());
        assertTrue(other.getTransfers().isEmpty());
    }

    @Test
    void versionIsBumpedOnEveryUpdate() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        long before = account.getVersion();

        account.credit(new BigDecimal("1.00"));
        accountRepository.flush();

        assertEquals(before + 1, account.getVersion());
    }

    @Test
    void staleUpdateIsRejectedByVersionCheck() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        // Simulates another transaction committing a change after we read the row.
        entityManager.createNativeQuery("UPDATE account SET version = version + 1 WHERE id = :id")
                .setParameter("id", account.getId())
                .executeUpdate();

        account.credit(new BigDecimal("1.00"));

        assertThrows(OptimisticLockingFailureException.class, accountRepository::flush);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890123456789012345", "123456789012345678901234567"}) // 25 and 27 chars
    void accountNumberMustBeExactly26Characters(String accountNumber) {
        Account account = new Account(null, accountNumber, new BigDecimal("100.00"), new Limits());

        // Bean Validation (@Size) runs when Hibernate flushes, before any SQL is sent.
        ConstraintViolationException ex = assertThrows(ConstraintViolationException.class,
                () -> accountRepository.saveAndFlush(account));

        assertEquals("accountNumber", ex.getConstraintViolations().iterator().next().getPropertyPath().toString());
    }

    @Test
    void databaseRejectsWrongLengthAccountNumberEvenWithoutJpa() {
        // Plain SQL skips @Size (like data.sql would), so only the schema.sql CHECK can stop it.
        var insert = entityManager.createNativeQuery("""
                INSERT INTO account (account_number, balance, transfer_limit, limit_used)
                VALUES ('123', 100.00, 10000.00, 0.00)""");

        PersistenceException ex = assertThrows(PersistenceException.class, insert::executeUpdate);

        assertTrue(ex.getMessage().toUpperCase().contains("CK_ACCOUNT_NUMBER_LENGTH"), ex.getMessage());
    }
}
