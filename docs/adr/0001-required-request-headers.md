# 0001. Required request headers

Status: Accepted
Date: 2026-11-13

## Context
Every API call must be authenticated and traceable across logs.

## Decision
`RequestHeadersInterceptor` checks every request:
- missing or wrong `Authorization: Bearer <app.security.token>` → 401
- missing `X-Correlation-Id` → 400

`WebConfig` builds and registers the interceptor.

## Consequences
- `@WebMvcTest` classes need `@Import(WebConfig.class)`, or the interceptor isn't active.
- Test requests need `TestHeaders.withValidHeaders(...)`.
