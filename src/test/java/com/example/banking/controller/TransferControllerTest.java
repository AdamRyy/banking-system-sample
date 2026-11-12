package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.service.TransferService;
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

import static com.example.banking.controller.AccountControllerTest.expectError;
import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import({WebConfig.class, FixedClockConfig.class})
class TransferControllerTest {

    private static final String URL = "/api/v1/transfers";
    private static final String VALID_BODY = "{\"sourceId\":1,\"targetId\":2,\"amount\":100}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    private ResultActions postJson(String body) throws Exception {
        return mockMvc.perform(withValidHeaders(post(URL)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createsTransferAndReturns201() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenReturn(new TransferResponse(1L, 2L, new BigDecimal("100.00"),
                        new BigDecimal("4900.00"), new BigDecimal("9900.00")));

        postJson(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value(1))
                .andExpect(jsonPath("$.targetId").value(2))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.accBalance").value(4900.00))
                .andExpect(jsonPath("$.remainingLimit").value(9900.00));
    }

    // ---- Business rule rejections ------------------------------------------

    @Test
    void returns422WhenTransferLimitExceeded() throws Exception {
        TransferLimitExceededException ex =
                new TransferLimitExceededException(1L, new BigDecimal("100.00"), new BigDecimal("50.00"));
        when(transferService.transfer(any(TransferRequest.class))).thenThrow(ex);

        expectError(postJson(VALID_BODY).andExpect(status().isUnprocessableContent()), "TRANSFER_LIMIT_EXCEEDED")
                .andExpect(jsonPath("$.message").value(ex.getMessage()));
    }

    @Test
    void returns422WhenInsufficientFunds() throws Exception {
        InsufficientFundsException ex =
                new InsufficientFundsException(1L, new BigDecimal("100.00"), new BigDecimal("50.00"));
        when(transferService.transfer(any(TransferRequest.class))).thenThrow(ex);

        expectError(postJson(VALID_BODY).andExpect(status().isUnprocessableContent()), "INSUFFICIENT_FUNDS")
                .andExpect(jsonPath("$.message").value(ex.getMessage()));
    }

    @Test
    void returns404WhenSourceAccountDoesNotExist() throws Exception {
        when(transferService.transfer(any(TransferRequest.class))).thenThrow(new AccountNotFoundException(1L));

        expectError(postJson(VALID_BODY).andExpect(status().isNotFound()), "ACCOUNT_NOT_FOUND");
    }

    // ---- Invalid requests ----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"sourceId\":1,\"targetId\":1,\"amount\":100}",     // same account
            "{\"sourceId\":0,\"targetId\":2,\"amount\":100}",     // zero id
            "{\"sourceId\":-1,\"targetId\":2,\"amount\":100}",    // negative id
            "{\"sourceId\":1,\"amount\":100}",                    // missing targetId
            "{\"sourceId\":1,\"targetId\":2,\"amount\":0}",       // zero amount
            "{\"sourceId\":1,\"targetId\":2,\"amount\":-10.50}",  // negative amount
            "{\"sourceId\":1,\"targetId\":2,\"amount\":100.555}", // more than 2 decimal places
            "{\"sourceId\":1,\"targetId\":2}"                     // missing amount
    })
    void rejectsInvalidRequestWith400(String body) throws Exception {
        expectError(postJson(body).andExpect(status().isBadRequest()), "VALIDATION_FAILED");

        verify(transferService, never()).transfer(any());
    }

    @Test
    void validationMessageNamesTheBrokenRule() throws Exception {
        expectError(postJson("{\"sourceId\":1,\"targetId\":1,\"amount\":100}")
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value("differentAccounts: sourceId and targetId must be different"));
    }

    @Test
    void validationMessageListsAllFieldsSortedByName() throws Exception {
        expectError(postJson("{\"sourceId\":0,\"targetId\":2,\"amount\":0}")
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value("amount: must be greater than 0; sourceId: must be greater than 0"));
    }

    @Test
    void validationMessagesStayEnglishForAnyClientLocale() throws Exception {
        // spring.web.locale-resolver=fixed: Accept-Language must not switch Bean Validation's language.
        expectError(mockMvc.perform(withValidHeaders(post(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "pl-PL")
                        .content("{\"sourceId\":0,\"targetId\":2,\"amount\":0}"))
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message").value("amount: must be greater than 0; sourceId: must be greater than 0"));
    }

    @Test
    void validationMessageExplainsAmountDigitLimits() throws Exception {
        expectError(postJson("{\"sourceId\":1,\"targetId\":2,\"amount\":100.555}")
                .andExpect(status().isBadRequest()), "VALIDATION_FAILED")
                .andExpect(jsonPath("$.message")
                        .value("amount: must have max 15 integer digits and 2 decimal places"));
    }

    @Test
    void rejectsMalformedJsonWith400() throws Exception {
        expectError(postJson("{\"sourceId\":1,\"targetId\":").andExpect(status().isBadRequest()), "MALFORMED_REQUEST");

        verify(transferService, never()).transfer(any());
    }

    @Test
    void rejectsWrongHttpMethodWith405() throws Exception {
        expectError(mockMvc.perform(withValidHeaders(get(URL))).andExpect(status().isMethodNotAllowed()), "METHOD_NOT_ALLOWED");
    }

    @Test
    void rejectsNonJsonContentTypeWith415() throws Exception {
        expectError(mockMvc.perform(withValidHeaders(post(URL)).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType()), "UNSUPPORTED_MEDIA_TYPE");
    }
}
