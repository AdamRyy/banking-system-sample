package com.example.banking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull @Positive Long sourceId,
        @NotNull @Positive Long targetId,
        @NotNull @Positive BigDecimal amount) {

    // Null ids are reported by @NotNull, so this rule only fires when both are present.
    @AssertTrue(message = "sourceId and targetId must be different")
    public boolean isDifferentAccounts() {
        return sourceId == null || !sourceId.equals(targetId);
    }
}
