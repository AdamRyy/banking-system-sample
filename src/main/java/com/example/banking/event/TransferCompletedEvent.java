package com.example.banking.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published to Kafka topic bank-transfers, keyed by sourceAccountNumber, after the transfer committed. */
public record TransferCompletedEvent(
        Long transferId,
        String sourceAccountNumber,
        String targetAccountNumber,
        BigDecimal amount,
        Instant timestamp
) {}
