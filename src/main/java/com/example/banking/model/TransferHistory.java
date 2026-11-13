package com.example.banking.model;

import com.example.banking.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One side of a transfer, seen from {@link #account}: OUTGOING means money went to
 * {@link #counterpartyAccountNumber}, INCOMING means it came from there. A transfer writes one row of each.
 */
@Entity
@Table(name = "transfer_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class TransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull
    @Size(min = Account.ACCOUNT_NUMBER_LENGTH, max = Account.ACCOUNT_NUMBER_LENGTH)
    @Column(name = "counterparty_account_number", length = Account.ACCOUNT_NUMBER_LENGTH, nullable = false)
    private String counterpartyAccountNumber;

    // 17 = 15 integer digits + 2 decimals, same as TransferRequest's @Digits.
    @Column(precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal amount;

    // "timestamp" is a keyword in H2, so the column is named executed_at.
    @Column(name = "executed_at", nullable = false)
    private Instant timestamp;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 8, nullable = false)
    private TransferDirection direction;

    /** Also adds the row to {@code account.getTransfers()}, so both sides of the relation stay in sync. */
    public TransferHistory(Account account, String counterpartyAccountNumber, BigDecimal amount,
                           Instant timestamp, TransferDirection direction) {
        this.account = account;
        this.counterpartyAccountNumber = counterpartyAccountNumber;
        this.amount = MoneyUtils.normalize(amount);
        this.timestamp = timestamp;
        this.direction = direction;
        account.addTransfer(this);
    }
}
