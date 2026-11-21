package com.example.banking.controller;

import com.example.banking.KafkaTestcontainersConfiguration;
import com.example.banking.TestcontainersConfiguration;
import com.example.banking.config.KafkaConfig;
import com.example.banking.consumer.FraudDetectionConsumer;
import com.example.banking.event.TransferCompletedEvent;
import com.example.banking.model.TransferDirection;
import com.example.banking.model.TransferHistory;
import com.example.banking.repository.TransferHistoryRepository;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional on purpose: a test transaction rolls back, so AFTER_COMMIT would never fire.
 * The transfers really commit, so @DirtiesContext throws this context (and its Postgres) away afterwards
 * instead of letting other tests reuse the changed balances.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class TransferEventIntegrationTest {

    private static final String SOURCE_ACCOUNT = "12345678901234567890123456";   // id 1 in data.sql
    private static final String TARGET_ACCOUNT = "65432109876543210987654321";   // id 2 in data.sql

    @Autowired
    private MockMvc mockMvc;

    // Boot's factory, so the records are read with the deserializer settings from application.yml.
    @Autowired
    private ConsumerFactory<String, TransferCompletedEvent> consumerFactory;

    private Consumer<String, TransferCompletedEvent> consumer;

    @Autowired
    private TransferHistoryRepository transferHistoryRepository;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @BeforeEach
    void subscribe() {
        // Fresh group per test, starting at the end, so each test sees only the records it caused.
        consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), null);
        consumer.subscribe(List.of(KafkaConfig.BANK_TRANSFERS_TOPIC));
        // One poll can return before the group join finishes. Seeking an empty assignment would do nothing,
        // and auto-offset-reset=earliest would then replay older records.
        Instant deadline = Instant.now().plusSeconds(30);
        while (consumer.assignment().isEmpty()) {
            assertThat(Instant.now()).as("no partitions assigned within 30s").isBefore(deadline);
            consumer.poll(Duration.ofMillis(200));
        }
        consumer.seekToEnd(consumer.assignment());
        consumer.assignment().forEach(consumer::position);   // resolve the lazy seek now, before the transfer
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    private void transfer(String amount, int expectedStatus) throws Exception {
        mockMvc.perform(withValidHeaders(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"targetId\":2,\"amount\":" + amount + "}"))
                .andExpect(status().is(expectedStatus));
    }

    /** The @KafkaListeners log on their own threads, so wait until the line shows up. */
    private static void awaitOutput(CapturedOutput output, String expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (!output.getOut().contains(expected)) {
            assertThat(Instant.now()).as("not logged within 30s: %s", expected).isBefore(deadline);
            Thread.sleep(200);
        }
    }

    /**
     * Waits until the group has committed past the record. The container commits only after the listener
     * returned, so after this an absent log line really means the listener chose not to log.
     */
    private void awaitProcessed(ConsumerRecord<?, ?> record) throws Exception {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            Instant deadline = Instant.now().plusSeconds(30);
            while (true) {
                OffsetAndMetadata committed = admin.listConsumerGroupOffsets(FraudDetectionConsumer.GROUP_ID)
                        .partitionsToOffsetAndMetadata().get().get(partition);
                if (committed != null && committed.offset() > record.offset()) {
                    return;
                }
                assertThat(Instant.now())
                        .as("%s didn't process the record within 30s", FraudDetectionConsumer.GROUP_ID)
                        .isBefore(deadline);
                Thread.sleep(200);
            }
        }
    }

    /** Ends at the line break, so transfer 1 doesn't match "Transfer ID: 13". */
    private static String fraudAlertFor(long transferId) {
        return "Transfer ID: " + transferId + "\n";
    }

    @Test
    void committedTransferPublishesEventKeyedBySourceAccount(CapturedOutput output) throws Exception {
        transfer("250.00", 201);

        ConsumerRecord<String, TransferCompletedEvent> record =
                KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.BANK_TRANSFERS_TOPIC, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(SOURCE_ACCOUNT);
        TransferCompletedEvent event = record.value();
        // Other tests in this context commit too, so match the row by amount. Its id is the transferId.
        List<TransferHistory> outgoing = transferHistoryRepository.findAll().stream()
                .filter(row -> row.getDirection() == TransferDirection.OUTGOING)
                .filter(row -> row.getAmount().compareTo(new BigDecimal("250.00")) == 0)
                .toList();
        assertThat(outgoing).hasSize(1);
        assertThat(event.transferId()).isEqualTo(outgoing.getFirst().getId());
        assertThat(event.sourceAccountNumber()).isEqualTo(SOURCE_ACCOUNT);
        assertThat(event.targetAccountNumber()).isEqualTo(TARGET_ACCOUNT);
        assertThat(event.amount()).isEqualByComparingTo("250.00");
        assertThat(event.timestamp()).isNotNull();

        awaitOutput(output, "Transfer " + event.transferId() + " completed.");
        awaitProcessed(record);
        assertThat(output.getOut()).doesNotContain(fraudAlertFor(event.transferId()));   // below the fraud threshold
    }

    @Test
    void largeTransferRaisesFraudAlert(CapturedOutput output) throws Exception {
        transfer("15000.00", 201);

        TransferCompletedEvent event = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.BANK_TRANSFERS_TOPIC,
                Duration.ofSeconds(10)).value();
        // The whole block, because the notification logs the same "Amount: ..." line.
        awaitOutput(output, "Suspicious transfer detected!\n" + fraudAlertFor(event.transferId())
                + "Amount: 15000.00 PLN");
        awaitOutput(output, "Transfer " + event.transferId() + " completed.");   // notification still sent
    }

    @Test
    void rejectedTransferPublishesNothing() throws Exception {
        transfer("999999.00", 422);   // more than the balance: rolled back

        assertThat(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3))).isEmpty();
    }
}
