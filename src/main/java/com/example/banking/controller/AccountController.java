package com.example.banking.controller;

import com.example.banking.dto.LimitsResponse;
import com.example.banking.dto.PageResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.service.AccountService;
import com.example.banking.service.TransferHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    static final int MAX_PAGE_SIZE = 100;

    private final AccountService accountService;
    private final TransferHistoryService transferHistoryService;

    // TODO: the daily limit resets only on a transfer, so after midnight this can show yesterday's "used"
    //  until the next transfer. Fix: compute the effective used/remaining on read via Account methods.
    @GetMapping("/{accountId}/limits")
    public LimitsResponse getLimits(@PathVariable @NotNull @Positive Long accountId) {
        return LimitsResponse.from(accountService.getLimits(accountId));
    }

    /** page/size are plain params, not Pageable: Pageable silently fixes bad values instead of rejecting them. */
    @GetMapping("/{accountId}/transfers")
    public PageResponse<TransferHistoryResponse> getTransfers(
            @PathVariable @NotNull @Positive Long accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @SortDefault(sort = "timestamp", direction = Sort.Direction.DESC) Sort sort) {
        return transferHistoryService.getTransferHistory(accountId, PageRequest.of(page, size, sort));
    }
}
