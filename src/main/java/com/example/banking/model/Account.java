package com.example.banking.model;

import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read access is public. State changes only through {@link #debit(BigDecimal)}, which keeps
 * the balance and the used limit consistent.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class Account {

    public static final int ACCOUNT_NUMBER_LENGTH = 26;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = ACCOUNT_NUMBER_LENGTH, max = ACCOUNT_NUMBER_LENGTH)
    @Column(name = "account_number", length = ACCOUNT_NUMBER_LENGTH, nullable = false, unique = true)
    private String accountNumber;

    @Column(precision = 17, scale = MoneyUtils.SCALE, nullable = false)
    private BigDecimal balance;

    @Embedded
    private Limits limits;

    /**
     * Optimistic lock: every UPDATE runs "WHERE version = ?" and bumps it. If another transaction
     * changed the row first, 0 rows match and the commit fails instead of overwriting its change.
     */
    @Version
    private Long version;

    /** Inverse side of {@link TransferHistory#getAccount()}; lazy ({@code @OneToMany} default), loaded on first access. */
    @OneToMany(mappedBy = "account")
    @OrderBy("timestamp ASC, id ASC") // oldest first; id breaks ties between rows of the same instant
    private List<TransferHistory> transfers = new ArrayList<>();

    public Account(Long id, String accountNumber, BigDecimal balance, Limits limits) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.balance = MoneyUtils.normalize(balance);
        this.limits = limits;
    }

    public void debit(BigDecimal amount) {
        if (!limits.allow(amount)) {
            throw new TransferLimitExceededException(id, amount, limits.getRemaining());
        }
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(id, amount, balance);
        }
        balance = balance.subtract(amount);
        limits.use(amount);
    }

    public List<TransferHistory> getTransfers() {
        return Collections.unmodifiableList(transfers);
    }

    /** Called only by the TransferHistory constructor. */
    void addTransfer(TransferHistory transfer) {
        transfers.add(transfer);
    }

    /** Incoming money: no limit applies, limits only cap outgoing transfers. */
    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }
}
