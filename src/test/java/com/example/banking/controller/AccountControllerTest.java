package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import com.example.banking.dto.PageResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InvalidSortException;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
import com.example.banking.service.AccountService;
import com.example.banking.service.TransferHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.example.banking.controller.TestHeaders.CORRELATION_ID;
import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockitoBean
    private TransferHistoryService transferHistoryService;

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferHistoryService).getTransferHistory(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTransferHistoryPage() throws Exception {
        TransferHistoryResponse outgoing = new TransferHistoryResponse(10L, TransferDirection.OUTGOING,
                "65432109876543210987654321", new BigDecimal("100.00"), Instant.parse("2026-11-12T10:15:30Z"));
        when(transferHistoryService.getTransferHistory(eq(1L), any()))
                .thenReturn(new PageResponse<>(List.of(outgoing), 0, 10, 11, 2));

        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?page=0&size=10")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value("65432109876543210987654321"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.content[0].timestamp").value("2026-11-12T10:15:30Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void passesPageSizeAndSortFromQueryToService() throws Exception {
        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?page=1&size=5&sort=amount,asc")))
                .andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "amount"));
    }

    @Test
    void usesNewestFirstPageOfTwentyByDefault() throws Exception {
        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers")))
                .andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "timestamp"));
    }

    @Test
    void acceptsMaximumPageSize() throws Exception {
        mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?size=100")))
                .andExpect(status().isOk());

        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "size=101 | size: must be less than or equal to 100",
            "size=0   | size: must be greater than or equal to 1",
            "page=-1  | page: must be greater than or equal to 0"})
    void rejectsOutOfRangePageParameters(String query, String message) throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?" + query)))
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value(message));

        verify(transferHistoryService, never()).getTransferHistory(any(), any());
    }

    @Test
    void rejectsNonNumericPageSize() throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?size=abc")))
                .andExpect(status().isBadRequest()), "INVALID_PARAMETER")
                .andExpect(jsonPath("$.message").value("size: invalid value 'abc'"));
    }

    @Test
    void transfersOfUnknownAccountReturnNotFound() throws Exception {
        when(transferHistoryService.getTransferHistory(eq(99L), any())).thenThrow(new AccountNotFoundException(99L));

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/99/transfers")))
                .andExpect(status().isNotFound()), "ACCOUNT_NOT_FOUND");
    }

    @Test
    void unknownSortPropertyReturnsBadRequest() throws Exception {
        when(transferHistoryService.getTransferHistory(eq(1L), any()))
                .thenThrow(new InvalidSortException("foo", List.of("amount", "id")));

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/transfers?sort=foo")))
                .andExpect(status().isBadRequest()), "INVALID_PARAMETER")
                .andExpect(jsonPath("$.message").value("sort: unknown property 'foo' (allowed: amount, id)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void transfersRejectNonPositiveAccountId(String accountId) throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/{id}/transfers", accountId)))
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED");

        verify(transferHistoryService, never()).getTransferHistory(any(), any());
    }

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
    void returnsInternalErrorWithoutLeakingDetails() throws Exception {
        when(accountService.getLimits(1L)).thenThrow(new IllegalStateException("db password is hunter2"));

        expectError(mockMvc.perform(withValidHeaders(get("/api/v1/accounts/1/limits")))
                .andExpect(status().isInternalServerError()), "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
