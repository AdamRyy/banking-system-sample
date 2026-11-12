package com.example.banking.controller;

import com.example.banking.model.Account;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransferHistoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * Full stack against the seed data in H2: real interceptor, services, repositories.
 * MockMvc runs on the test thread, so @Transactional rolls every test back and the shared
 * in-memory DB stays as data.sql left it.
 */
@SpringBootTest
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

    /** Reads from H2, not from the persistence context. */
    private BigDecimal balanceOf(long id) {
        entityManager.flush();
        entityManager.clear();
        return accountRepository.findById(id).map(Account::getBalance).orElseThrow();
    }

    /** All history rows, read from H2. data.sql seeds none, so each test starts from an empty table. */
    private List<TransferHistory> history() {
        entityManager.flush();
        entityManager.clear();
        return transferHistoryRepository.findAll();
    }

    @Test
    void transferMovesMoneyBetweenSeedAccounts() throws Exception {
        transfer(1, 2, "100")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accBalance").value(4900.00))
                .andExpect(jsonPath("$.remainingLimit").value(9900.00));

        assertEquals(new BigDecimal("4900.00"), balanceOf(1));
        assertEquals(new BigDecimal("1100.00"), balanceOf(2));

        List<TransferHistory> history = history();
        assertEquals(1, history.size());
        TransferHistory record = history.getFirst();
        assertEquals(1L, record.getAccount().getId());
        assertEquals("65432109876543210987654321", record.getCounterpartyAccountNumber());
        assertEquals(new BigDecimal("100.00"), record.getAmount());
        assertNotNull(record.getTimestamp());   // real system clock: only check it was set

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(100.00))
                .andExpect(jsonPath("$.remaining").value(9900.00));
    }

    @Test
    void unknownTargetIsRejectedAndSourceUnchanged() throws Exception {
        transfer(1, 99, "100")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertEquals(new BigDecimal("5000.00"), balanceOf(1));
        assertTrue(history().isEmpty());
    }

    @Test
    void insufficientFundsLeavesBothAccountsUnchanged() throws Exception {
        transfer(2, 1, "1000.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertEquals(new BigDecimal("1000.00"), balanceOf(2));
        assertEquals(new BigDecimal("5000.00"), balanceOf(1));
        assertTrue(history().isEmpty());
    }
}
