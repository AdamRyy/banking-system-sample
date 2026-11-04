package com.example.banking.config;

import com.example.banking.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Replaces Tomcat's HTML error page for requests rejected before they reach the application,
 * such as a URL with an encoded slash or an illegal character. Those never get forwarded to /error.
 */
@Slf4j
public class JsonErrorReportValve extends ErrorReportValve {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        // Leave alone anything that isn't an error, already has a body or was already reported.
        if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        HttpStatus resolved = HttpStatus.resolve(response.getStatus());
        HttpStatus status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse body = ErrorResponse.forStatus(status, RequestHeadersInterceptor.correlationIdOf(request));
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(MAPPER.writeValueAsString(body));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException ex) {
            log.warn("Could not write error response", ex);
        }
    }
}
