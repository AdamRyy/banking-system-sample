# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring Boot 4.1 (Java 25, Lombok) sample banking REST API. There is no database: state is one hardcoded in-memory account (id 1) held by `AccountService`.

## Commands

```bash
./mvnw test                               # all tests
./mvnw test -Dtest=TransferServiceTest    # one test class
./mvnw test -Dtest=TransferServiceTest#methodName   # one test method
./mvnw spring-boot:run                    # start on port 8080
```

`requests/transfer-curls.sh` changes the in-memory state, so run it against a freshly started app.

## Architecture

- **Required headers:** `RequestHeadersInterceptor` rejects requests without `Authorization: Bearer <app.security.token>` (401) or `X-Correlation-Id` (400). `WebConfig` builds and registers it, so `@WebMvcTest` classes need `@Import(WebConfig.class)`, and test requests need `TestHeaders.withValidHeaders(...)`.
- **Errors:** every error response must be `ErrorResponse` JSON. Two layers produce it:
  1. `GlobalExceptionHandler`: exceptions from controllers, services and the interceptor. To add an error, add the exception, an `ErrorCode` entry (which sets the HTTP status) and a handler.
  2. `ApiErrorController`: errors the container forwards to `/error`.

  Requests Tomcat rejects before they reach the app (e.g. an encoded slash `%2F`) get Tomcat's default error page; that's an accepted gap.
- **Money:** use `BigDecimal` with 2 decimal places via `MoneyUtils.normalize`. Compare with `compareTo`, because `equals` also compares scale.
- **Transfers:** `TransferService` only debits the source account; the target account isn't touched or checked.
