package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import com.example.banking.model.Limits;
import com.example.banking.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.banking.controller.TestHeaders.CORRELATION_ID;
import static com.example.banking.controller.TestHeaders.CORRELATION_ID_HEADER;
import static com.example.banking.controller.TestHeaders.VALID_AUTHORIZATION;
import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(WebConfig.class)
class RequestHeadersInterceptorTest {

    private static final String URL = "/api/v1/accounts/1/limits";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void validHeadersPassAndCorrelationIdIsEchoed() throws Exception {
        when(accountService.getLimits(1L)).thenReturn(new Limits());

        mockMvc.perform(withValidHeaders(get(URL)))
                .andExpect(status().isOk())
                .andExpect(header().string(CORRELATION_ID_HEADER, CORRELATION_ID));
    }

    @Test
    void missingAuthorizationReturns401WithChallengeAndCorrelationId() throws Exception {
        mockMvc.perform(get(URL).header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$['X-Correlation-Id']").value(CORRELATION_ID));

        verify(accountService, never()).getLimits(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer wrong-token", "Basic token-123", "token-123", "Bearer ", "Bearer token-1234"})
    void invalidAuthorizationReturns401(String authorization) throws Exception {
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(accountService, never()).getLimits(any());
    }

    @Test
    void missingCorrelationIdReturns400AndOmitsItFromBody() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_CORRELATION_ID"))
                .andExpect(jsonPath("$['X-Correlation-Id']").doesNotExist());

        verify(accountService, never()).getLimits(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankCorrelationIdReturns400(String correlationId) throws Exception {
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION)
                        .header(CORRELATION_ID_HEADER, correlationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_CORRELATION_ID"));

        verify(accountService, never()).getLimits(any());
    }
}
