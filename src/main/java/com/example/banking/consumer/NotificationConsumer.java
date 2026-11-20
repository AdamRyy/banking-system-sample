package com.example.banking.consumer;

import com.example.banking.config.KafkaConfig;
import com.example.banking.event.TransferCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    public static final String GROUP_ID = "notification-service-group";

    // required = false: a tombstone (null value) reaches the guard instead of failing argument resolution.
    @KafkaListener(id = "notification", topics = KafkaConfig.BANK_TRANSFERS_TOPIC, groupId = GROUP_ID)
    public void on(@Payload(required = false) TransferCompletedEvent event) {
        // Retrying can't fix a malformed record, so skip it instead of throwing into the error handler.
        if (event == null || event.amount() == null) {
            log.error("Skipping malformed TransferCompletedEvent: {}", event);
            return;
        }
        log.info("""
                [NOTIFICATION]
                Transfer {} completed.
                SMS sent to customer.
                Amount: {} PLN""", event.transferId(), event.amount().toPlainString());
    }
}
