# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring Boot 4.1 (Java 25, Lombok, Spring Data JPA) sample banking REST API. State lives in PostgreSQL 17 running in Docker (`docker-compose.yml`; the app itself runs on the host), rebuilt on every start: `schema.sql` drops and creates the tables (Hibernate only validates them, `ddl-auto=validate`) and `data.sql` seeds two test accounts (ids 1 and 2). Change the schema in `schema.sql` and the entity together, or startup fails. `docker-compose.yml` also starts a single-node Kafka broker (KRaft, `localhost:9092`, container `banking-kafka`). The app connects to it (`spring.kafka.*`) and creates topic `bank-transfers` (3 partitions, RF 1) through the `NewTopic` bean in `KafkaConfig`. `spring.kafka.admin.fail-fast=true`, so `spring-boot:run` needs Kafka running too, and every `@SpringBootTest` must import `KafkaTestcontainersConfiguration` (the `@DataJpaTest` classes don't). Config is `src/main/resources/application.yml` (no `.properties`). `TransferService` publishes a Spring `TransferCompletedEvent`, and `TransferEventPublisher` (`@TransactionalEventListener(AFTER_COMMIT)`) sends it as JSON to `bank-transfers`, keyed by `sourceAccountNumber`; `transferId` is the OUTGOING history row's id. A rollback publishes nothing, so a `@SpringBootTest` that checks events must not be `@Transactional` (see `TransferEventIntegrationTest`, which uses `@DirtiesContext` because it commits). Consumer settings (group `banking-system`, JSON via `ErrorHandlingDeserializer`) are configured, and two `@KafkaListener`s in `consumer` read `bank-transfers`, each in its own group so both get every event: `NotificationConsumer` (`notification-service-group`; each log line names its consumer thread, partition and offset) logs every transfer, `FraudDetectionConsumer` (`fraud-detection-group`) logs a `[FRAUD ALERT]` for amounts above 10 000 PLN. Seed account 1 (balance 50 000, daily limit 20 000) can exceed that threshold. Kafka UI (`banking-kafka-ui`) runs on http://localhost:8081 and reaches the broker through its container-only listener `kafka:29092`; host clients use `localhost:9092`.

## Commands

```bash
cp .env.example .env                      # once: DB credentials (git-ignored), read by compose and the app
docker compose up -d                      # start PostgreSQL first (needed for spring-boot:run)
./mvnw test                               # unit tests only
./mvnw verify                             # unit + integration tests (needs Docker: Testcontainers)
./mvnw test -Dtest=TransferServiceTest    # one unit test class
./mvnw test -Dtest=TransferServiceTest#methodName   # one unit test method
./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=TransferIntegrationTest   # one integration test class
./mvnw spring-boot:run                    # start on port 8080
```

Unit tests live in `src/test/java`, integration tests in `src/integrationTest/java` (see ADR 0006). Maven picks the runner
by class name, not by directory: `*IntegrationTest` runs in Failsafe, everything else in Surefire.

Transfers change the data until the app restarts, so run manual requests against a freshly started app.

## Conventions

- **Comments:** keep Javadoc and comments short, and add them only where the code is complicated or misleading (e.g. a hidden lazy load, no `save()` because of dirty checking). Don't comment code that already explains itself.
- **Database schema:** `schema.sql` holds only basic constraints: PRIMARY KEY, NOT NULL, UNIQUE, FOREIGN KEY, DEFAULT. No CHECK constraints and no native ENUM types. Business rules (lengths, ranges, allowed values) live in Java: Bean Validation on the entities (runs at flush) or domain methods. Enums are `VARCHAR` columns, mapped with `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`. Index names start with `idx_`.

## Architecture

Architecture decisions live in `docs/adr`, one ADR per decision (index: `docs/adr/README.md`):

@docs/adr/0001-required-request-headers.md
@docs/adr/0002-uniform-error-responses.md
@docs/adr/0003-money-as-bigdecimal.md
@docs/adr/0004-transfers-transaction-and-optimistic-locking.md
@docs/adr/0006-separate-integration-test-source-set.md
@docs/adr/0007-paginated-list-endpoints.md
@docs/adr/0008-postgresql-in-docker.md
@docs/adr/0009-transfer-events-on-kafka.md
