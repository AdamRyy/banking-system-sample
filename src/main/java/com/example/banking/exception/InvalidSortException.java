package com.example.banking.exception;

import java.util.Collection;
import java.util.stream.Collectors;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Collection<String> allowed) {
        super("sort: unknown property '%s' (allowed: %s)".formatted(property,
                allowed.stream().sorted().collect(Collectors.joining(", "))));
    }
}
