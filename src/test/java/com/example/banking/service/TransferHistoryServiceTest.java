package com.example.banking.service;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.TransferHistoryResponse;
import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.InvalidSortException;
import com.example.banking.model.Account;
import com.example.banking.model.Limits;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransferHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferHistoryRepository transferHistoryRepository;

    @InjectMocks
    private TransferHistoryService transferHistoryService;

    private final Account account =
            new Account(1L, "12345678901234567890123456", new BigDecimal("5000.00"), new Limits());

    private Pageable queriedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferHistoryRepository).findByAccountId(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    void unknownAccountThrowsWithoutQueryingHistory() {
        when(accountRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> transferHistoryService.getTransferHistory(99L, PageRequest.of(0, 10)))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(transferHistoryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "account.balance"})
    void disallowedSortPropertyThrows(String property) {
        when(accountRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> transferHistoryService.getTransferHistory(1L,
                PageRequest.of(0, 10, Sort.by(property))))
                .isInstanceOf(InvalidSortException.class)
                .hasMessage("sort: unknown property '%s' "
                        + "(allowed: amount, counterpartyAccountNumber, direction, id, timestamp)", property);
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void addsIdTieBreakerInTheDirectionOfTheFirstOrder() {
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferHistoryRepository.findByAccountId(eq(1L), any())).thenReturn(Page.empty());

        transferHistoryService.getTransferHistory(1L, PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "timestamp")));

        Pageable pageable = queriedPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));
    }

    @Test
    void keepsSortUnchangedWhenItAlreadyContainsId() {
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferHistoryRepository.findByAccountId(eq(1L), any())).thenReturn(Page.empty());

        transferHistoryService.getTransferHistory(1L, PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(queriedPageable().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void mapsPageToDtosAndKeepsTotals() {
        TransferHistory history = account.recordTransfer("65432109876543210987654321", new BigDecimal("100"),
                NOW, TransferDirection.OUTGOING);
        PageRequest request = PageRequest.of(0, 1);
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferHistoryRepository.findByAccountId(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(history), request, 3));

        PageResponse<TransferHistoryResponse> result = transferHistoryService.getTransferHistory(1L, request);

        assertThat(result).isEqualTo(new PageResponse<>(List.of(new TransferHistoryResponse(null,
                TransferDirection.OUTGOING, "65432109876543210987654321", new BigDecimal("100.00"), NOW)), 0, 1, 3, 3));
    }
}
