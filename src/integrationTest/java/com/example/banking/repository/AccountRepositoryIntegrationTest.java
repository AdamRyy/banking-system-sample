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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(accounts).hasSize(2);
        Account first = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        Account second = accountRepository.findByAccountNumber(SECOND).orElseThrow();
        assertThat(first.getBalance()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(second.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(first.getLimits().getRemaining()).isEqualTo(new BigDecimal("20000.00"));
    }

    @Test
    void dailyLimitResetAtRoundTripsWithoutChangingTheDay() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        assertThat(account.getLimits().getResetAt())
                .isEqualTo(Instant.parse("2026-11-11T00:00:00Z")); // seeded as already expired

        account.debit(new BigDecimal("1.00"), Clock.fixed(NOW, ZoneOffset.UTC)); // starts a new window ending today
        accountRepository.flush();
        entityManager.clear();

        Account reloaded = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        assertThat(reloaded.getLimits().getResetAt()).isEqualTo(Instant.parse("2026-11-12T23:59:59.999999Z"));
    }

    @Test
    void versionIsBumpedOnEveryUpdate() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        long before = account.getVersion();

        account.credit(new BigDecimal("1.00"));
        accountRepository.flush();

        assertThat(account.getVersion()).isEqualTo(before + 1);
    }

    @Test
    void staleUpdateIsRejectedByVersionCheck() {
        Account account = accountRepository.findByAccountNumber(FIRST).orElseThrow();
        // Simulates another transaction committing a change after we read the row.
        entityManager.createNativeQuery("UPDATE accounts SET version = version + 1 WHERE id = :id")
                .setParameter("id", account.getId())
                .executeUpdate();

        account.credit(new BigDecimal("1.00"));

        assertThatThrownBy(accountRepository::flush).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890123456789012345", "123456789012345678901234567"}) // 25 and 27 chars
    void accountNumberMustBeExactly26Characters(String accountNumber) {
        Account account = new Account(null, accountNumber, new BigDecimal("100.00"), new Limits());

        // Bean Validation (@Size) runs when Hibernate flushes, before any SQL is sent.
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> accountRepository.saveAndFlush(account))
                .satisfies(ex -> assertThat(ex.getConstraintViolations())
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .containsExactly("accountNumber"));
    }
}
