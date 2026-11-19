package com.example.banking.repository;

import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.banking.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs against Testcontainers Postgres built from schema.sql + data.sql; each test rolls back. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class AccountRepositoryIntegrationTest {

    private static final String FIRST = "12345678901234567890123456";
    private static final String SECOND = "65432109876543210987654321";
    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");

    @Autowired
    private AccountRepository accountRepository;

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
    void dailyLimitResetAtRoundTripsWithoutChangingTheDay() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), account.getLimits().getResetAt()); // seeded as already expired

        account.debit(new BigDecimal("1.00"), Clock.fixed(NOW, ZoneOffset.UTC)); // starts a new window ending today
        accountRepository.flush();
        entityManager.clear();

        Account reloaded = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        assertEquals(Instant.parse("2026-11-12T23:59:59.999999Z"), reloaded.getLimits().getResetAt());
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
        entityManager.createNativeQuery("UPDATE accounts SET version = version + 1 WHERE id = :id")
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
}
