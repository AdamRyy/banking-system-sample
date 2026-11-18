package com.example.banking.controller;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.LimitsResponse;
import com.example.banking.service.AccountService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    // TODO: the daily limit resets only on a transfer, so after midnight this can show yesterday's "used"
    //  until the next transfer. Fix: compute the effective used/remaining on read via Account methods.
    @GetMapping("/{accountId}/limits")
    public LimitsResponse getLimits(@PathVariable @NotNull @Positive Long accountId) {
        return LimitsResponse.from(accountService.getLimits(accountId));
    }
}
