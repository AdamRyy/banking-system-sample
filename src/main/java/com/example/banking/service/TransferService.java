package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.model.Account;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.TransferHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountService accountService;
    private final TransferHistoryRepository transferHistoryRepository;
    private final Clock clock;


    /**
     * One transaction: both balances change or neither does. There is no save() call, because
     * the accounts are managed entities and JPA flushes their changes (with the @Version check) on commit.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        Account source = accountService.getAccount(request.sourceId()); // 404 if unknown
        Account target = accountService.getAccount(request.targetId()); // 404 if unknown
        source.debit(request.amount());                                 // 422 if limit or funds are short
        target.credit(request.amount());

        // save transfer in history
        TransferHistory record = new TransferHistory(
                source,
                target.getAccountNumber(),
                request.amount(),
                clock.instant());

        transferHistoryRepository.save(record);
        return TransferResponse.from(request, source);
    }
}
