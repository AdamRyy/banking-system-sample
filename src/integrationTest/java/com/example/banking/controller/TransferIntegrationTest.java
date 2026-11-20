package com.example.banking.controller;

import com.example.banking.KafkaTestcontainersConfiguration;
import com.example.banking.TestcontainersConfiguration;
import com.example.banking.model.Account;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransferHistoryRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack against the seed data in Testcontainers Postgres: real interceptor, services, repositories.
 * MockMvc runs on the test thread, so @Transactional rolls every test back and the shared
 * database stays as data.sql left it.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@AutoConfigureMockMvc
@Transactional
class TransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferHistoryRepository transferHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    private ResultActions transfer(long sourceId, long targetId, String amount) throws Exception {
        return mockMvc.perform(withValidHeaders(post("/api/v1/transfers"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceId\":%d,\"targetId\":%d,\"amount\":%s}".formatted(sourceId, targetId, amount)));
    }

    /** Reads from the database, not from the persistence context. */
    private BigDecimal balanceOf(long id) {
        entityManager.flush();
        entityManager.clear();
        return accountRepository.findById(id).map(Account::getBalance).orElseThrow();
    }

    /** All history rows, read from the database. data.sql seeds none, so each test starts from an empty table. */
    private List<TransferHistory> history() {
        entityManager.flush();
        entityManager.clear();
        return transferHistoryRepository.findAll();
    }

    @Test
    void transferMovesMoneyBetweenSeedAccounts() throws Exception {
        transfer(1, 2, "100")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accBalance").value(49900.00))
                .andExpect(jsonPath("$.remainingLimit").value(19900.00));

        assertEquals(new BigDecimal("49900.00"), balanceOf(1));
        assertEquals(new BigDecimal("1100.00"), balanceOf(2));

        List<TransferHistory> history = history().stream()
                .sorted(Comparator.comparing(h -> h.getAccount().getId()))
                .toList();
        assertEquals(2, history.size());

        TransferHistory outgoing = history.getFirst();
        assertEquals(1L, outgoing.getAccount().getId());
        assertEquals(TransferDirection.OUTGOING, outgoing.getDirection());
        assertEquals("65432109876543210987654321", outgoing.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.00"), outgoing.getAmount());
        assertNotNull(outgoing.getTimestamp());   // real system clock: only check it was set

        TransferHistory incoming = history.get(1);
        assertEquals(2L, incoming.getAccount().getId());
        assertEquals(TransferDirection.INCOMING, incoming.getDirection());
        assertEquals("12345678901234567890123456", incoming.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.00"), incoming.getAmount());
        assertEquals(outgoing.getTimestamp(), incoming.getTimestamp());

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(100.00))
                .andExpect(jsonPath("$.remaining").value(19900.00));
    }

    @Test
    void transferHistoryIsPagedPerAccount() throws Exception {
        transfer(1, 2, "100").andExpect(status().isCreated());
        transfer(1, 2, "50").andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?page=0&size=1&sort=amount,desc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value("65432109876543210987654321"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/2/transfers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[1].direction").value("INCOMING"));
    }

    @Test
    void transferHistoryOfUnknownAccountIsNotFound() throws Exception {
        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/99/transfers")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void transferHistoryRejectsSortThroughTheAccountRelation() throws Exception {
        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?sort=account.balance,desc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /** Guards against N+1: the lazy account must never be loaded while mapping rows to DTOs. */
    @Test
    void transferHistoryPageRunsAFixedNumberOfQueries() throws Exception {
        for (int i = 0; i < 3; i++) {
            transfer(1, 2, "10").andExpect(status().isCreated());
        }
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?size=2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        assertEquals(3, stats.getPrepareStatementCount());   // existsById + page select + count
        assertEquals(2, stats.getEntityLoadCount());          // the 2 history rows; no Account was loaded
    }

    @Test
    void unknownTargetIsRejectedAndSourceUnchanged() throws Exception {
        transfer(1, 99, "100")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertEquals(new BigDecimal("50000.00"), balanceOf(1));
        assertTrue(history().isEmpty());
    }

    @Test
    void insufficientFundsLeavesBothAccountsUnchanged() throws Exception {
        transfer(2, 1, "1000.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertEquals(new BigDecimal("1000.00"), balanceOf(2));
        assertEquals(new BigDecimal("50000.00"), balanceOf(1));
        assertTrue(history().isEmpty());
    }
}
