package com.example.banking.dto;

import com.example.banking.model.Limits;

import java.math.BigDecimal;

public record LimitsResponse(BigDecimal limit, BigDecimal used, BigDecimal remaining) {

    public static LimitsResponse from(Limits limits) {
        return new LimitsResponse(limits.getLimit(), limits.getUsed(), limits.getRemaining());
    }
}
