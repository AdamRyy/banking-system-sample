# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring Boot 4.1 (Java 25, Lombok, Spring Data JPA) sample banking REST API. State lives in PostgreSQL 17 running in Docker (`docker-compose.yml`; the app itself runs on the host), rebuilt on every start: `schema.sql` drops and creates the tables (Hibernate only validates them, `ddl-auto=validate`) and `data.sql` seeds two test accounts (ids 1 and 2). Change the schema in `schema.sql` and the entity together, or startup fails.

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
