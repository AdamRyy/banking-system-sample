# 0008. PostgreSQL in Docker, Testcontainers for integration tests

Status: Accepted
Date: 2026-11-19

## Context
The in-memory H2 database differs from a production database in SQL dialect, types and keywords, so passing
tests didn't prove the app works on a real engine.

## Decision
- The app uses PostgreSQL 17. For local development it runs from `docker-compose.yml` (container
  `banking-postgres`, port 5432). The Spring app runs on the host, not in Docker.
- Credentials (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) live only in a git-ignored `.env`, and the
  committed template is `.env.example`. Compose reads `.env` on its own, and Spring imports it through
  `spring.config.import=optional:file:.env[.properties]`. Real environment variables override the file.
- The data is reset on every start, the same as with H2: `spring.sql.init.mode=always` runs `schema.sql`, which
  begins with `DROP TABLE IF EXISTS` (children first), then `data.sql`. Hibernate still only validates.
- Integration tests import `TestcontainersConfiguration`, which starts a `postgres:17-alpine` container. The
  container's DataSource is wired in through `@ServiceConnection`, so `@DataJpaTest` doesn't replace it with an
  embedded database.

## Consequences
- `./mvnw verify` needs Docker running. `./mvnw test` (unit tests) doesn't.
- No volume and no migrations. When data has to survive a restart, switch to Flyway and add a volume (new ADR).
- When a table is added, add its `DROP TABLE IF EXISTS` to the top of `schema.sql`, before the tables it references.
- Each test context (`@DataJpaTest`, `@SpringBootTest`) starts its own container, so integration tests take a few seconds longer.
