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
 * An event published outside a transaction is dropped silently, so {@code TransferService.transfer} must stay
 * {@code @Transactional}.
 * <p>
 * Keyed by source account: that account's outgoing transfers stay in order on one partition. Its incoming
 * transfers are keyed by other accounts, so there's no order across an account's incoming and outgoing events.
 * <p>
 * Known gap: the DB commit and the Kafka send aren't atomic. If the send fails after the commit (broker down,
 * {@code max.block.ms} or {@code delivery.timeout.ms} exceeded), the event is lost and only logged; if the JVM
 * dies while the record is still in the producer buffer, it's lost silently. A transactional outbox would close
 * this gap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final KafkaTemplate<String, TransferCompletedEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TransferCompletedEvent event) {
        try {
            kafkaTemplate.send(KafkaConfig.BANK_TRANSFERS_TOPIC, event.sourceAccountNumber(), event)
                    .whenComplete((_, ex) -> {
                        if (ex != null) {
                            logLost(event, ex);
                        }
                    });
        } catch (RuntimeException ex) {
            // send() throws synchronously on a metadata timeout (broker down) or a serialization error.
            // The transfer is already committed, so this must not reach the client.
            logLost(event, ex);
        }
    }

    private static void logLost(TransferCompletedEvent event, Throwable ex) {
        log.error("Lost TransferCompletedEvent for transfer {}: {}", event.transferId(), event, ex);
    }
}
