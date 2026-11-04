package com.example.banking.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API error codes returned in {@link com.example.banking.dto.ErrorResponse#code()},
 * each bound to the HTTP status it is sent with.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
}
