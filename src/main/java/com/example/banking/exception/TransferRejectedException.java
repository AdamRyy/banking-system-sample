package com.example.banking.exception;

/**
 * Base type for transfers that are well-formed but refused by business rules.
 * Mapped to 422 by GlobalExceptionHandler.
 */
public abstract class TransferRejectedException extends RuntimeException {

    protected TransferRejectedException(String message) {
        super(message);
    }
}
