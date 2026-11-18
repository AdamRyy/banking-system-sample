package com.example.banking.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for a page; Spring Data doesn't guarantee one for PageImpl. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
