# 0002. Uniform error responses

Status: Accepted
Date: 2026-11-13

## Context
Clients should parse a single error format, whatever layer failed.

## Decision
Every error response is `ErrorResponse` JSON. Two layers produce it:
1. `GlobalExceptionHandler`: exceptions from controllers, services and the interceptor.
2. `ApiErrorController`: errors the container forwards to `/error`.

To add an error, add the exception, an `ErrorCode` entry (which sets the HTTP status) and a handler.

## Consequences
Requests Tomcat rejects before they reach the app (e.g. an encoded slash `%2F`) get Tomcat's default
error page. That's an accepted gap.
