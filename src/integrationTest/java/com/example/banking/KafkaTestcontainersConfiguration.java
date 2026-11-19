package com.example.banking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Throwaway Kafka broker, same image as docker-compose.yml. Separate from {@link TestcontainersConfiguration}
 * so the {@code @DataJpaTest} classes don't start a broker. Every {@code @SpringBootTest} needs it, because
 * {@code spring.kafka.admin.fail-fast=true} fails the context without one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.0");
    }
}
