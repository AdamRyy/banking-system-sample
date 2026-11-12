package com.example.banking.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedProperties(TransferRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestHasNoViolations() {
        assertTrue(violatedProperties(new TransferRequest(1L, 2L, new BigDecimal("100"))).isEmpty());
    }

    @Test
    void sameSourceAndTargetIsRejected() {
        assertEquals(Set.of("differentAccounts"),
                violatedProperties(new TransferRequest(1L, 1L, new BigDecimal("100"))));
    }

    @Test
    void nonPositiveIdsAreRejected() {
        assertEquals(Set.of("sourceId", "targetId"),
                violatedProperties(new TransferRequest(0L, -3L, new BigDecimal("100"))));
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertEquals(Set.of("amount"), violatedProperties(new TransferRequest(1L, 2L, BigDecimal.ZERO)));
        assertEquals(Set.of("amount"), violatedProperties(new TransferRequest(1L, 2L, new BigDecimal("-0.01"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.5", "100.50", "0.01", "999999999999999.99"})
    void amountWithinDigitLimitsIsAccepted(String amount) {
        assertTrue(violatedProperties(new TransferRequest(1L, 2L, new BigDecimal(amount))).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "100.555",              // 3 decimal places
            "100.500",              // scale 3, even though numerically 2 places
            "0.001",                // 3 decimal places
            "1000000000000000.00"   // 16 integer digits
    })
    void amountOutsideDigitLimitsIsRejected(String amount) {
        assertEquals(Set.of("amount"), violatedProperties(new TransferRequest(1L, 2L, new BigDecimal(amount))));
    }

    @Test
    void nullFieldsAreRejectedWithoutSameAccountViolation() {
        assertEquals(Set.of("sourceId", "targetId", "amount"),
                violatedProperties(new TransferRequest(null, null, null)));
    }
}
