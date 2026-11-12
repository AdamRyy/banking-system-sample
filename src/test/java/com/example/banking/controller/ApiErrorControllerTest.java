package com.example.banking.controller;

import com.example.banking.config.WebConfig;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.banking.controller.TestHeaders.CORRELATION_ID;
import static com.example.banking.controller.TestHeaders.CORRELATION_ID_HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiErrorController.class)
@Import({WebConfig.class, FixedClockConfig.class})
class ApiErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void containerClientErrorUsesErrorResponseFormat() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").value(FixedClockConfig.NOW))
                .andExpect(jsonPath("$['X-Correlation-Id']").value(CORRELATION_ID));
    }

    @Test
    void containerServerErrorDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "db password is hunter2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$['X-Correlation-Id']").doesNotExist());
    }

    @Test
    void errorPathCalledDirectlyReturns404() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
