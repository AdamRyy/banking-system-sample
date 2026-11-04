package com.example.banking.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Needs a real Tomcat: MockMvc never goes through the container, so it can't reproduce these rejections. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonErrorReportValveIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void urlRejectedByTomcatGetsJsonInsteadOfHtmlPage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/accounts/%2F/limits"))
                .header("X-Correlation-Id", "abc-123")
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                response.headers().toString());
        assertTrue(response.body().contains("\"code\":\"BAD_REQUEST\""), response.body());
        assertTrue(response.body().contains("\"X-Correlation-Id\":\"abc-123\""), response.body());
    }
}
