package com.example.banking.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferEventPublisherTest {

    @Mock
    private KafkaTemplate<String, TransferCompletedEvent> kafkaTemplate;

    @InjectMocks
    private TransferEventPublisher publisher;

    @Test
    void sendsEventToBankTransfersKeyedBySourceAccountNumber() {
        TransferCompletedEvent event = new TransferCompletedEvent(7L, "12345678901234567890123456",
                "65432109876543210987654321", new BigDecimal("100.50"), Instant.parse("2026-11-12T10:15:30Z"));
        CompletableFuture<SendResult<String, TransferCompletedEvent>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send("bank-transfers", "12345678901234567890123456", event)).thenReturn(sent);

        publisher.on(event);

        verify(kafkaTemplate).send("bank-transfers", "12345678901234567890123456", event);
    }

    @Test
    void failedSendDoesNotThrow() {
        TransferCompletedEvent event = new TransferCompletedEvent(7L, "12345678901234567890123456",
                "65432109876543210987654321", new BigDecimal("100.50"), Instant.parse("2026-11-12T10:15:30Z"));
        when(kafkaTemplate.send("bank-transfers", event.sourceAccountNumber(), event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.on(event);   // only logs: the transfer is already committed
    }
}
