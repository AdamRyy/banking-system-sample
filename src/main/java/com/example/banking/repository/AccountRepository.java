package com.example.banking.repository;

import com.example.banking.model.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = "transfers")
    @Query("select a from Account a order by a.id")
    List<Account> findAllWithTransfers();
}
