package com.example.banking.controller;

import com.example.banking.dto.LimitsResponse;
import com.example.banking.service.AccountService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountId}/limits")
    public LimitsResponse getLimits(@PathVariable @NotNull @Positive Long accountId) {
        return LimitsResponse.from(accountService.getLimits(accountId));
    }
}
