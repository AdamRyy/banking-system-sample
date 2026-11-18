package com.example.banking.dto;

import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferHistoryResponse(Long id, TransferDirection direction, String counterpartyAccountNumber,
                                      BigDecimal amount, Instant timestamp) {

    public static TransferHistoryResponse from(TransferHistory history) {
        return new TransferHistoryResponse(history.getId(), history.getDirection(),
                history.getCounterpartyAccountNumber(), history.getAmount(), history.getTimestamp());
    }
}
