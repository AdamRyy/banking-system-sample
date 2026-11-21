package com.example.banking.consumer;

import com.example.banking.event.TransferCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class FraudDetectionConsumerTest {

    private final FraudDetectionConsumer consumer = new FraudDetectionConsumer();

    private static TransferCompletedEvent event(String amount) {
        return new TransferCompletedEvent(101L, "12345678901234567890123456",
                "65432109876543210987654321", new BigDecimal(amount), Instant.parse("2026-11-20T10:00:00Z"));
    }

    @Test
    void amountAboveThresholdRaisesAlert(CapturedOutput output) {
        consumer.on(event("15000.00"));

        assertThat(output.getOut()).contains("[FRAUD ALERT]", "Suspicious transfer detected!", "Transfer ID: 101",
                "Amount: 15000.00 PLN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000.00", "9999.99"})
    void amountUpToThresholdIsNotSuspicious(String amount, CapturedOutput output) {
        consumer.on(event(amount));

        assertThat(output.getOut()).doesNotContain("[FRAUD ALERT]");
    }

    @Test
    void tombstoneIsSkipped(CapturedOutput output) {
        consumer.on(null);

        assertThat(output.getOut()).contains("Skipping malformed TransferCompletedEvent");
    }

    @Test
    void eventWithoutAmountIsSkipped(CapturedOutput output) {
        consumer.on(new TransferCompletedEvent(101L, null, null, null, null));   // what "{}" deserializes to

        assertThat(output.getOut())
                .contains("Skipping malformed TransferCompletedEvent")
                .doesNotContain("[FRAUD ALERT]");
    }
}
