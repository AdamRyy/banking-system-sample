package com.example.banking.repository;

import com.example.banking.model.Account;
import com.example.banking.model.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {
    List<TransferHistory> findByAccount(Account account);

}
