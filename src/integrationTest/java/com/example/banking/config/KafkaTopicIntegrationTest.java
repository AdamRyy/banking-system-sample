package com.example.banking.config;

import com.example.banking.KafkaTestcontainersConfiguration;
import com.example.banking.TestcontainersConfiguration;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class KafkaTopicIntegrationTest {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void bankTransfersTopicIsCreatedWithThreePartitionsAndOneReplica() {
        TopicDescription topic = kafkaAdmin.describeTopics(KafkaConfig.BANK_TRANSFERS_TOPIC)
                .get(KafkaConfig.BANK_TRANSFERS_TOPIC);

        assertEquals(3, topic.partitions().size());
        for (TopicPartitionInfo partition : topic.partitions()) {
            assertEquals(1, partition.replicas().size());
        }
    }
}
