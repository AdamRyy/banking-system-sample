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
import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(balanceOf(1)).isEqualTo(new BigDecimal("49900.00"));
        assertThat(balanceOf(2)).isEqualTo(new BigDecimal("1100.00"));

        List<TransferHistory> history = history().stream()
                .sorted(Comparator.comparing(h -> h.getAccount().getId()))
                .toList();
        assertThat(history).hasSize(2);

        TransferHistory outgoing = history.getFirst();
        assertThat(outgoing.getAccount().getId()).isEqualTo(1L);
        assertThat(outgoing.getDirection()).isEqualTo(TransferDirection.OUTGOING);
        assertThat(outgoing.getCounterpartyAccountNumber()).isEqualTo("65432109876543210987654321");
        assertThat(outgoing.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(outgoing.getTimestamp()).isNotNull();   // real system clock: only check it was set

        TransferHistory incoming = history.get(1);
        assertThat(incoming.getAccount().getId()).isEqualTo(2L);
        assertThat(incoming.getDirection()).isEqualTo(TransferDirection.INCOMING);
        assertThat(incoming.getCounterpartyAccountNumber()).isEqualTo("12345678901234567890123456");
        assertThat(incoming.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(incoming.getTimestamp()).isEqualTo(outgoing.getTimestamp());

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

        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);   // existsById + page select + count
        assertThat(stats.getEntityLoadCount()).isEqualTo(2);          // the 2 history rows; no Account was loaded
    }

    @Test
    void unknownTargetIsRejectedAndSourceUnchanged() throws Exception {
        transfer(1, 99, "100")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(balanceOf(1)).isEqualTo(new BigDecimal("50000.00"));
        assertThat(history()).isEmpty();
    }

    @Test
    void insufficientFundsLeavesBothAccountsUnchanged() throws Exception {
        transfer(2, 1, "1000.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(2)).isEqualTo(new BigDecimal("1000.00"));
        assertThat(balanceOf(1)).isEqualTo(new BigDecimal("50000.00"));
        assertThat(history()).isEmpty();
    }
}
