# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring Boot 4.1 (Java 25, Lombok, Spring Data JPA) sample banking REST API. State lives in an in-memory H2 database, rebuilt on every start: `schema.sql` creates the tables (Hibernate only validates them, `ddl-auto=validate`) and `data.sql` seeds two test accounts (ids 1 and 2). Change the schema in `schema.sql` and the entity together, or startup fails.

## Commands

```bash
./mvnw test                               # all tests
./mvnw test -Dtest=TransferServiceTest    # one test class
./mvnw test -Dtest=TransferServiceTest#methodName   # one test method
./mvnw spring-boot:run                    # start on port 8080
```

Transfers change the H2 data until the app restarts, so run manual requests against a freshly started app.

## Conventions

- **Comments:** keep Javadoc and comments short, and add them only where the code is complicated or misleading (e.g. a hidden lazy load, no `save()` because of dirty checking). Don't comment code that already explains itself.

## Architecture

- **Required headers:** `RequestHeadersInterceptor` rejects requests without `Authorization: Bearer <app.security.token>` (401) or `X-Correlation-Id` (400). `WebConfig` builds and registers it, so `@WebMvcTest` classes need `@Import(WebConfig.class)`, and test requests need `TestHeaders.withValidHeaders(...)`.
- **Errors:** every error response must be `ErrorResponse` JSON. Two layers produce it:
  1. `GlobalExceptionHandler`: exceptions from controllers, services and the interceptor. To add an error, add the exception, an `ErrorCode` entry (which sets the HTTP status) and a handler.
  2. `ApiErrorController`: errors the container forwards to `/error`.

  Requests Tomcat rejects before they reach the app (e.g. an encoded slash `%2F`) get Tomcat's default error page; that's an accepted gap.
- **Money:** use `BigDecimal` with 2 decimal places via `MoneyUtils.normalize`. Compare with `compareTo`, because `equals` also compares scale.
- **Transfers:** `TransferService.transfer` is one `@Transactional` unit: it loads source then target (404 if either is missing), debits the source (422 on limit or funds) and credits the target. There's no `save()`: JPA dirty checking writes both on commit. `Account` has `@Version`, so a concurrent change fails the commit with `OptimisticLockingFailureException` → 409 `CONCURRENT_MODIFICATION`. `TransferHistory` exists but isn't written yet.
- **Tests:** service tests are Mockito unit tests (mocked `AccountRepository`). Persistence, commit and rollback are covered by `PersistenceTest` (`@DataJpaTest`) and `TransferIntegrationTest` (`@SpringBootTest` + MockMvc + `@Transactional`, so each test rolls back).
