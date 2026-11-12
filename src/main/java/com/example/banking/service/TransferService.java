package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.model.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountService accountService;

    public TransferResponse transfer(TransferRequest request) {
        Account source = accountService.getAccount(request.sourceId()); // 404 if unknown
        source.debit(request.amount());                                 // 422 if limit or funds are short

        // Target account is out of scope for now: only the source is debited.
        return TransferResponse.from(request, source);
    }
}
