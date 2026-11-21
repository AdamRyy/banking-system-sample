package com.example.banking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    public static final String BANK_TRANSFERS_TOPIC = "bank-transfers";

    /**
     * Created by Boot's KafkaAdmin at startup if missing. An existing topic keeps its replication factor,
     * and its partition count is only ever raised, never lowered.
     */
    @Bean
    NewTopic bankTransfersTopic() {
        return TopicBuilder.name(BANK_TRANSFERS_TOPIC).partitions(3).replicas(1).build();
    }
}
