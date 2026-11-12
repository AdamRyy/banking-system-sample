package com.example.banking.dto;

import com.example.banking.model.Account;
import com.example.banking.util.MoneyUtils;

import java.math.BigDecimal;

public record TransferResponse(Long sourceId, Long targetId, BigDecimal amount, BigDecimal accBalance, BigDecimal remainingLimit) {

    public static TransferResponse from(TransferRequest request, Account source) {
        return new TransferResponse(
                request.sourceId(),
                request.targetId(),
                MoneyUtils.normalize(request.amount()),
                source.getBalance(),
                source.getLimits().getRemaining());
    }
}
