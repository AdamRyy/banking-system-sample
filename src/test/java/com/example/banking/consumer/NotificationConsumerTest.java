package com.example.banking.consumer;

import com.example.banking.event.TransferCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class NotificationConsumerTest {

    private final NotificationConsumer consumer = new NotificationConsumer();

    @Test
    void logsNotificationForEveryTransfer(CapturedOutput output) {
        consumer.on(new TransferCompletedEvent(101L, "12345678901234567890123456",
                "65432109876543210987654321", new BigDecimal("1500.00"), Instant.parse("2026-11-20T10:00:00Z")));

        assertThat(output.getOut()).contains("[NOTIFICATION]", "Transfer 101 completed.", "SMS sent to customer.",
                "Amount: 1500.00 PLN");
    }

    @Test
    void tombstoneIsSkipped(CapturedOutput output) {
        consumer.on(null);

        assertThat(output.getOut())
                .contains("Skipping malformed TransferCompletedEvent")
                .doesNotContain("[NOTIFICATION]");
    }

    @Test
    void eventWithoutAmountIsSkipped(CapturedOutput output) {
        consumer.on(new TransferCompletedEvent(101L, null, null, null, null));   // what "{}" deserializes to

        assertThat(output.getOut())
                .contains("Skipping malformed TransferCompletedEvent")
                .doesNotContain("[NOTIFICATION]");
    }
}
