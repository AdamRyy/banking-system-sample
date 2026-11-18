package com.example.banking.service;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InvalidSortException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransferHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransferHistoryService {

    /** Only the DTO's own fields: a path like "account.balance" would add a join to accounts. */
    static final Set<String> ALLOWED_SORT = Set.of("id", "timestamp", "amount", "direction", "counterpartyAccountNumber");

    private final AccountRepository accountRepository;
    private final TransferHistoryRepository transferHistoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<TransferHistoryResponse> getTransferHistory(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        validateSort(pageable.getSort());
        return PageResponse.from(transferHistoryRepository
                .findByAccountId(accountId, withIdTieBreaker(pageable))
                .map(TransferHistoryResponse::from));
    }

    private static void validateSort(Sort sort) {
        sort.stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !ALLOWED_SORT.contains(property))
                .findFirst()
                .ifPresent(property -> {
                    throw new InvalidSortException(property, ALLOWED_SORT);
                });
    }

    /** Rows with equal sort values (e.g. both sides of one transfer share a timestamp) keep a stable order across pages. */
    private static Pageable withIdTieBreaker(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            Sort.Direction direction = sort.stream().findFirst().map(Sort.Order::getDirection).orElse(Sort.Direction.ASC);
            sort = sort.and(Sort.by(direction, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
