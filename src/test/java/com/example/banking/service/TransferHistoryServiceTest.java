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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertThrows(AccountNotFoundException.class,
                () -> transferHistoryService.getTransferHistory(99L, PageRequest.of(0, 10)));

        verifyNoInteractions(transferHistoryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "account.balance"})
    void disallowedSortPropertyThrows(String property) {
        when(accountRepository.existsById(1L)).thenReturn(true);

        InvalidSortException ex = assertThrows(InvalidSortException.class,
                () -> transferHistoryService.getTransferHistory(1L, PageRequest.of(0, 10, Sort.by(property))));

        assertEquals("sort: unknown property '%s' (allowed: amount, counterpartyAccountNumber, direction, id, timestamp)"
                .formatted(property), ex.getMessage());
        verifyNoInteractions(transferHistoryRepository);
    }

    @Test
    void addsIdTieBreakerInTheDirectionOfTheFirstOrder() {
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferHistoryRepository.findByAccountId(eq(1L), any())).thenReturn(Page.empty());

        transferHistoryService.getTransferHistory(1L, PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "timestamp")));

        Pageable pageable = queriedPageable();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")), pageable.getSort());
    }

    @Test
    void keepsSortUnchangedWhenItAlreadyContainsId() {
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferHistoryRepository.findByAccountId(eq(1L), any())).thenReturn(Page.empty());

        transferHistoryService.getTransferHistory(1L, PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id")));

        assertEquals(Sort.by(Sort.Direction.ASC, "id"), queriedPageable().getSort());
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

        assertEquals(new PageResponse<>(List.of(new TransferHistoryResponse(null, TransferDirection.OUTGOING,
                "65432109876543210987654321", new BigDecimal("100.00"), NOW)), 0, 1, 3, 3), result);
    }
}
