package com.example.banking.controller;

import com.example.banking.config.RequestHeadersInterceptor;
import com.example.banking.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Replaces Spring Boot's default /error handling, so errors that never reach GlobalExceptionHandler
 * (container errors, response.sendError) still use the ErrorResponse format.
 * A plain @Controller on purpose: WebConfig prefixes every @RestController with the API base path,
 * but the container forwards errors to exactly /error.
 */
@Controller
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        String correlationId = RequestHeadersInterceptor.correlationIdOf(request);
        HttpStatus status = resolveStatus(request);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.forStatus(status, correlationId));
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        // No status attribute means a client called /error directly, not a container forward.
        if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code)) {
            return HttpStatus.NOT_FOUND;
        }
        HttpStatus status = HttpStatus.resolve(code);
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
