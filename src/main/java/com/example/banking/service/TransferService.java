package com.example.banking.service;

import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.TransferResponse;
import com.example.banking.exception.InsufficientFundsException;
import com.example.banking.exception.TransferLimitExceededException;
import com.example.banking.model.Account;
import com.example.banking.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountService accountService;

    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        Account source = accountService.getAccount(request.sourceId()); // 404 if unknown

        if(source.getLimits().isTransferLimitExceeded(amount)) {
            throw new TransferLimitExceededException(source.getId(), amount, source.getLimits().getTransfer());
        }
        if(!source.hasSufficientFunds(amount)) {
            throw new InsufficientFundsException(source.getId(), amount, source.getBalance());
        }
        source.setBalance(source.getBalance().subtract(amount));
        source.getLimits().subtractTransfer(amount);

        // Target account is out of scope for now: only the source is debited.
        return TransferResponse.from(request, source);
    }
}
