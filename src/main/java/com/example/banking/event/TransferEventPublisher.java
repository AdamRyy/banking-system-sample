package com.example.banking.event;

import com.example.banking.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards {@link TransferCompletedEvent} to Kafka once the transfer's transaction has committed. A rollback
 * (404, 422, or a 409 at commit) discards the event, so consumers never see a transfer that didn't happen.
 * <p>
 * Known gap: the DB commit and the Kafka send aren't atomic. If the send fails after the commit (broker down,
 * {@code delivery.timeout.ms} exceeded), the event is lost and only logged; if the JVM dies while the record is
 * still in the producer buffer, it's lost silently. A transactional outbox would close this gap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final KafkaTemplate<String, TransferCompletedEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TransferCompletedEvent event) {
        // Keyed by source account: its transfers land on one partition, in order.
        kafkaTemplate.send(KafkaConfig.BANK_TRANSFERS_TOPIC, event.sourceAccountNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Lost TransferCompletedEvent for transfer {}: {}", event.transferId(), event, ex);
                    }
                });
    }
}
