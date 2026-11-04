package com.example.banking.exception;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Missing or invalid bearer token");
    }
}
