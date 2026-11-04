package com.example.banking.exception;

public class MissingCorrelationIdException extends RuntimeException {

    public MissingCorrelationIdException() {
        super("Header X-Correlation-Id is required");
    }
}
