package com.example.banking.repository;

import com.example.banking.model.TransferHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {

    /** Filters on the account_id FK column, without joining accounts. */
    Page<TransferHistory> findByAccountId(Long accountId, Pageable pageable);
}
