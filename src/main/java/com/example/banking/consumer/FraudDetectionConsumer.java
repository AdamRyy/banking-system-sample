package com.example.banking.consumer;

import com.example.banking.config.KafkaConfig;
import com.example.banking.event.TransferCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class FraudDetectionConsumer {

    public static final String GROUP_ID = "fraud-detection-group";

    static final BigDecimal THRESHOLD = new BigDecimal("10000.00");

    // required = false: a tombstone (null value) reaches the guard instead of failing argument resolution.
    @KafkaListener(topics = KafkaConfig.BANK_TRANSFERS_TOPIC, groupId = GROUP_ID)
    public void on(@Payload(required = false) TransferCompletedEvent event) {
        // Retrying can't fix a malformed record, so skip it instead of throwing into the error handler.
        if (event == null || event.amount() == null) {
            log.error("Skipping malformed TransferCompletedEvent: {}", event);
            return;
        }
        if (event.amount().compareTo(THRESHOLD) > 0) {
            log.warn("""
                    [FRAUD ALERT]
                    Suspicious transfer detected!
                    Transfer ID: {}
                    Amount: {} PLN""", event.transferId(), event.amount().toPlainString());
        }
    }
}
