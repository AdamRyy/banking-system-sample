# 0006. Separate source set for integration tests

Status: Accepted
Date: 2026-11-16
Supersedes: 0005

## Context
Fast unit tests and tests that start Spring or a database were mixed in `src/test/java` and always ran together.

## Decision
- `src/test/java`: unit tests, run by Surefire in `./mvnw test`. Service tests are Mockito tests (mocked
  `AccountRepository`). Controllers are tested with `@WebMvcTest`.
- `src/integrationTest/java`: tests that need a Spring context or a database (Testcontainers Postgres, see 0008), run by Failsafe in `./mvnw verify`:
  - `AccountRepositoryIntegrationTest` and `TransferHistoryIntegrationTest` (`@DataJpaTest`): mappings, constraints, locking.
  - `TransferIntegrationTest` (`@SpringBootTest` + MockMvc + `@Transactional`): the full request path.
- `build-helper-maven-plugin` adds `src/integrationTest/java` as a second test source root. Surefire excludes
  `**/*IntegrationTest.java` and Failsafe includes only that pattern.

## Consequences
- Both roots compile into `target/test-classes`, so integration tests can use package-private helpers from
  `src/test/java` (e.g. `controller/TestHeaders`).
- The runner is chosen by class name, not directory: an integration test must be named `*IntegrationTest`, and a
  unit test must not be.
- Each `@DataJpaTest` and integration test rolls back, so tests don't affect each other's data.
