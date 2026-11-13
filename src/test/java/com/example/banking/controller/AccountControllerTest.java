package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
import com.example.banking.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.example.banking.controller.TestHeaders.CORRELATION_ID;
import static com.example.banking.controller.TestHeaders.CORRELATION_ID_HEADER;
import static com.example.banking.controller.TestHeaders.VALID_AUTHORIZATION;
import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({WebConfig.class, FixedClockConfig.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    /** Common ErrorResponse shape: code + message + timestamp + the correlation id sent by withValidHeaders. */
    static ResultActions expectError(ResultActions actions, String code) throws Exception {
        return actions
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").value(FixedClockConfig.NOW))
                .andExpect(jsonPath("$['X-Correlation-Id']").value(CORRELATION_ID));
    }

    @Test
    void returnsLimitsForExistingAccount() throws Exception {
        when(accountService.getLimits(1L)).thenReturn(new Limits());

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(10000.00))
                .andExpect(jsonPath("$.used").value(0.00))
                .andExpect(jsonPath("$.remaining").value(10000.00));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        when(accountService.getLimits(2L)).thenThrow(new AccountNotFoundException(2L));

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/2/limits")))
                .andExpect(status().isNotFound()), "ACCOUNT_NOT_FOUND")
                .andExpect(jsonPath("$.message").value("Account with id 2 not found"));
    }

    @Test
    void endpointIsNotAvailableWithoutBasePrefix() throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/accounts/1/limits")))
                .andExpect(status().isNotFound()), "NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void returnsBadRequestForNonPositiveAccountId(String accountId) throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/{id}/limits", accountId)))
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value("accountId: must be greater than 0"));

        verify(accountService, never()).getLimits(any());
    }

    @Test
    void returnsBadRequestForNonNumericAccountId() throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/abc/limits")))
                .andExpect(status().isBadRequest()), "INVALID_PARAMETER")
                .andExpect(jsonPath("$.message").value("accountId: invalid value 'abc'"));
    }

    @Test
    void springClientErrorWithoutDedicatedHandlerKeepsItsStatusAndStaysJson() throws Exception {
        when(accountService.getLimits(1L)).thenReturn(new Limits());

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)), "NOT_ACCEPTABLE");
    }

    @Test
    void returnsAllAccountsWithTheirTransfers() throws Exception {
        TransferHistoryResponse outgoing = new TransferHistoryResponse(10L, TransferDirection.OUTGOING,
                "65432109876543210987654321", new BigDecimal("100.00"), Instant.parse("2026-11-12T10:15:30Z"));
        TransferHistoryResponse incoming = new TransferHistoryResponse(11L, TransferDirection.INCOMING,
                "12345678901234567890123456", new BigDecimal("100.00"), Instant.parse("2026-11-12T10:15:30Z"));
        when(accountService.getAllAccounts()).thenReturn(List.of(
                new AccountResponse(1L, "12345678901234567890123456", new BigDecimal("4900.00"), List.of(outgoing)),
                new AccountResponse(2L, "65432109876543210987654321", new BigDecimal("1100.00"), List.of(incoming))));

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("12345678901234567890123456"))
                .andExpect(jsonPath("$[0].balance").value(4900.00))
                .andExpect(jsonPath("$[0].transfers[0].id").value(10))
                .andExpect(jsonPath("$[0].transfers[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$[0].transfers[0].counterpartyAccountNumber").value("65432109876543210987654321"))
                .andExpect(jsonPath("$[0].transfers[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].transfers[0].timestamp").value("2026-11-12T10:15:30Z"))
                .andExpect(jsonPath("$[1].transfers[0].direction").value("INCOMING"));
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoAccounts() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(List.of());

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsEmptyTransfersForAccountWithoutHistory() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(List.of(
                new AccountResponse(1L, "12345678901234567890123456", new BigDecimal("5000.00"), List.of())));

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].transfers").isArray())
                .andExpect(jsonPath("$[0].transfers").isEmpty());
    }

    @Test
    void allAccountsRequiresAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(accountService, never()).getAllAccounts();
    }

    @Test
    void allAccountsRequiresCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_CORRELATION_ID"));

        verify(accountService, never()).getAllAccounts();
    }

    @Test
    void returnsInternalErrorWithoutLeakingDetails() throws Exception {
        when(accountService.getLimits(1L)).thenThrow(new IllegalStateException("db password is hunter2"));

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")))
                .andExpect(status().isInternalServerError()), "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
