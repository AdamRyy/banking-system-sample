package com.example.banking.dto;

import com.example.banking.model.Limits;

import java.math.BigDecimal;

public record LimitsResponse(BigDecimal transfer) {

    public static LimitsResponse from(Limits limits) {
        return new LimitsResponse(limits.getTransfer());
    }
}
