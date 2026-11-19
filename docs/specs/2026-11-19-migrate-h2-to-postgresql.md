# Plan: Move banking-system-sample from H2 to PostgreSQL in Docker

## Context
The app uses an in-memory H2 database today. The goal is a real PostgreSQL that runs in Docker through `docker-compose.yml`. The Spring app keeps running on the host (`./mvnw spring-boot:run`) and is not containerised yet.

Decisions you made:
- **Reset on every start.** The app behaves as it does with H2: each start drops the tables, recreates them and loads the two test accounts again.
- **Integration tests use Testcontainers.** Each run starts its own throwaway Postgres container, so tests never touch the dev database.

Branch: `chore/migrate-h2-to-postgresql` (already checked out, clean). Port 5432 on the host is free.

## Changes

### 1. `docker-compose.yml` (new, at the project root)
- Service `postgres`, image `postgres:17-alpine`, container name `banking-postgres`.
- Environment: `POSTGRES_DB=banking`, `POSTGRES_USER=banking`, `POSTGRES_PASSWORD=banking` (local development only).
- Ports: `5432:5432`.
- Healthcheck: `pg_isready -U banking -d banking`.
- No volume, because the app wipes the data on every start anyway.

### 2. `pom.xml`
- Remove `com.h2database:h2`.
- Add `org.postgresql:postgresql` (scope runtime).
- Add three test-scope dependencies: `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql` and `org.testcontainers:testcontainers-junit-jupiter`. Their versions come from the Boot 4.1 BOM, which uses Testcontainers 2.x artifact names.

### 3. `src/main/resources/application.properties`
- Remove `spring.datasource.generate-unique-name=true` and the comment about H2.
- Add the connection settings. Each one can be overridden by an environment variable, with a default that matches the compose file:
  - `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/banking}`
  - `spring.datasource.username=${DB_USERNAME:banking}`
  - `spring.datasource.password=${DB_PASSWORD:banking}`
- Keep `spring.sql.init.mode=always` (it is required for a non-embedded database) and `ddl-auto=validate`. Update the comment to say: "Postgres in Docker, rebuilt from schema.sql + data.sql on every start".

### 4. `src/main/resources/schema.sql`
- Add `DROP TABLE IF EXISTS ...` statements at the top so the script can run again on every start. `transfer_history` is dropped before `accounts`, because of the foreign key.
- The rest of the DDL and `data.sql` work on Postgres as they are, so they stay unchanged.

### 5. Integration tests on Testcontainers
- New file `src/integrationTest/java/com/example/banking/TestcontainersConfiguration.java`: a `@TestConfiguration(proxyBeanMethods = false)` with one bean, `@Bean @ServiceConnection PostgreSQLContainer postgres()` using the `postgres:17-alpine` image.
- Add `@Import(TestcontainersConfiguration.class)` to `TransferIntegrationTest`, `AccountRepositoryIntegrationTest` and `TransferHistoryIntegrationTest`.
- `@ServiceConnection` stops `@DataJpaTest` from swapping in an embedded database. The existing rollback-per-test behaviour (ADR 0005/0006) stays as it is.
- Unit tests in `src/test` don't use a database, so they need no changes.

### 6. Docs
- `README.md`: prerequisites (Docker Desktop, JDK 25), then these commands:
  - `docker compose up -d` starts Postgres.
  - `docker compose ps` checks that it is healthy.
  - `./mvnw spring-boot:run` starts the app.
  - `docker compose exec postgres psql -U banking -d banking` opens a SQL shell.
  - `docker compose logs -f postgres` follows the database logs.
  - `docker compose down` stops Postgres.
  - `./mvnw verify` runs the tests (Docker must be running for Testcontainers).
- `CLAUDE.md`: replace the H2 wording with Postgres in Docker plus Testcontainers, and add `docker compose up -d` to the commands.
- New file `docs/adr/0008-postgresql-in-docker.md`, plus an entry in the `docs/adr/README.md` index.
- Save this plan to `docs/specs/2026-11-19-migrate-h2-to-postgresql.md`, following the repo's convention.

## Verification
1. `docker compose up -d`, then `docker compose ps` shows the container as `healthy`.
2. `./mvnw verify` runs all unit and integration tests and they pass. The log shows Testcontainers starting Postgres.
3. `./mvnw spring-boot:run` starts the app. The log shows the PostgreSQL dialect, and Hibernate validation passes.
4. Call an endpoint with curl, using the `token-123` bearer token and the headers required by ADR 0001. Make a transfer, restart the app, and check that the balances are back to 5000 / 1000 (the reset works).
5. `docker compose exec postgres psql -U banking -d banking -c "\dt"` lists `accounts` and `transfer_history`.
6. Do not commit until you ask for it.
