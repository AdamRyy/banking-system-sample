package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.model.Limits;
import com.example.banking.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(WebConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    /** Common ErrorResponse shape: code + message + timestamp, correlation id omitted while null. */
    static ResultActions expectError(ResultActions actions, String code) throws Exception {
        return actions
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$['X-Correlation-Id']").doesNotExist());
    }

    @Test
    void returnsLimitsForExistingAccount() throws Exception {
        when(accountService.getLimits(1L)).thenReturn(new Limits());

        mockMvc.perform(get("/api/v1/accounts/1/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer").value(10000.00));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        when(accountService.getLimits(2L)).thenThrow(new AccountNotFoundException(2L));

        expectError(mockMvc.perform(get("/api/v1/accounts/2/limits"))
                .andExpect(status().isNotFound()), "ACCOUNT_NOT_FOUND")
                .andExpect(jsonPath("$.message").value("Account with id 2 not found"));
    }

    @Test
    void endpointIsNotAvailableWithoutBasePrefix() throws Exception {
        expectError(mockMvc.perform(get("/accounts/1/limits"))
                .andExpect(status().isNotFound()), "NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void returnsBadRequestForNonPositiveAccountId(String accountId) throws Exception {
        expectError(mockMvc.perform(get("/api/v1/accounts/{id}/limits", accountId))
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value("accountId: must be greater than 0"));

        verify(accountService, never()).getLimits(any());
    }

    @Test
    void returnsBadRequestForNonNumericAccountId() throws Exception {
        expectError(mockMvc.perform(get("/api/v1/accounts/abc/limits"))
                .andExpect(status().isBadRequest()), "INVALID_PARAMETER")
                .andExpect(jsonPath("$.message").value("accountId: invalid value 'abc'"));
    }

    @Test
    void returnsInternalErrorWithoutLeakingDetails() throws Exception {
        when(accountService.getLimits(1L)).thenThrow(new IllegalStateException("db password is hunter2"));

        expectError(mockMvc.perform(get("/api/v1/accounts/1/limits"))
                .andExpect(status().isInternalServerError()), "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
