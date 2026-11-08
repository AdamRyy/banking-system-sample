package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Limits {

    private BigDecimal transfer;

    public Limits() {
        this.transfer = new BigDecimal("10000.00");
    }


    public void subtractTransfer(BigDecimal amount) {
        transfer = MoneyUtils.normalize(transfer.subtract(amount));
    }

    public boolean isTransferLimitExceeded(BigDecimal amount) {
        return amount.compareTo(transfer) > 0;
    }
}
