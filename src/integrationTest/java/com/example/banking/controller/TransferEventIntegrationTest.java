package com.example.banking.controller;

import com.example.banking.KafkaTestcontainersConfiguration;
import com.example.banking.TestcontainersConfiguration;
import com.example.banking.config.KafkaConfig;
import com.example.banking.event.TransferCompletedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.example.banking.controller.TestHeaders.withValidHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class TransferEventIntegrationTest {

    private static final String SOURCE_ACCOUNT = "12345678901234567890123456";   // id 1 in data.sql
    private static final String TARGET_ACCOUNT = "65432109876543210987654321";   // id 2 in data.sql

    @Autowired
    private MockMvc mockMvc;

    // Boot's factory, so the records are read with the deserializer settings from application.yml.
    @Autowired
    private ConsumerFactory<String, TransferCompletedEvent> consumerFactory;

    private Consumer<String, TransferCompletedEvent> consumer;

    @BeforeEach
    void subscribe() {
        // Fresh group per test, starting at the end, so each test sees only the records it caused.
        consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), null);
        consumer.subscribe(List.of(KafkaConfig.BANK_TRANSFERS_TOPIC));
        consumer.poll(Duration.ofSeconds(5));   // join the group and get partitions assigned
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

    @Test
    void committedTransferPublishesEventKeyedBySourceAccount() throws Exception {
        transfer("250.00", 201);

        ConsumerRecord<String, TransferCompletedEvent> record =
                KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.BANK_TRANSFERS_TOPIC, Duration.ofSeconds(10));

        assertEquals(SOURCE_ACCOUNT, record.key());
        TransferCompletedEvent event = record.value();
        assertNotNull(event.transferId());
        assertEquals(SOURCE_ACCOUNT, event.sourceAccountNumber());
        assertEquals(TARGET_ACCOUNT, event.targetAccountNumber());
        assertEquals(0, new BigDecimal("250.00").compareTo(event.amount()));
        assertNotNull(event.timestamp());
    }

    @Test
    void rejectedTransferPublishesNothing() throws Exception {
        transfer("999999.00", 422);   // more than the balance: rolled back

        assertTrue(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3)).isEmpty());
    }
}
