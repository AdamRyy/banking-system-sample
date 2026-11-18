package com.example.banking.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountResponse(Long id, String accountNumber, BigDecimal balance, List<TransferHistoryResponse> transfers) {
}
