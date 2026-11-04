package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Account {

    private Long id;
    private BigDecimal balance;
    private Limits limits;

    public Account() {
        this.id = 1L;
        this.balance = new BigDecimal("5000.00");
        this.limits = new Limits();
    }

    public void setBalance(BigDecimal balance) {
        this.balance = MoneyUtils.normalize(balance);
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
