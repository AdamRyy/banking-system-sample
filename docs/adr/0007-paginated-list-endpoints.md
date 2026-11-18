# 0007. Paginated list endpoints

Status: Accepted
Date: 2026-11-18

## Context
History grows with every transfer, so returning it unbounded (the removed `GET /accounts`) doesn't scale. Clients
need a stable page format and a stable order between pages.

## Decision
List endpoints (first one: `GET /api/v1/accounts/{accountId}/transfers`) take `?page=0&size=10&sort=timestamp,desc`
and return `PageResponse<T>`: `{content, page, size, totalElements, totalPages}`. Never return `Page`/`PageImpl`
itself: its JSON shape isn't stable.

- Limits are set per endpoint, not globally: `page` and `size` are `@RequestParam`s with Bean Validation
  (`@Min(0) page`, `@Min(1) @Max(100) size`), so out-of-range values → 400 `VALIDATION_FAILED`. Not a
  `Pageable` parameter: its resolver silently caps or resets bad values instead of rejecting them.
- `sort` is a Spring Data `Sort` with `@SortDefault` (transfers: newest first). Default `size` is 20.
- Sortable properties are a whitelist of the DTO's own fields (`TransferHistoryService.ALLOWED_SORT`). Anything else
  → 400 `INVALID_PARAMETER` via `InvalidSortException`.
- The service appends `id` as a tie-breaker, so rows with equal sort values don't move between pages.
- An unknown parent (account) → 404, never an empty page.
- The query filters on the FK (`findByAccountId` → `where account_id = ?`), and the DTO reads only the row's own
  columns, so the lazy `@ManyToOne` is never loaded.

## Consequences
- One request runs 3 statements: the parent's `existsById`, the page select and the count (Spring Data skips the count
  when the first page isn't full). `TransferIntegrationTest` asserts this, so an N+1 shows up as a test failure.
- Without the whitelist, an unknown property would become a 500 (`PropertyReferenceException`), and a path like
  `account.balance` would add a join and sort by data the client can't see.
- Offset paging: rows inserted while a client pages can shift items to the next page. Acceptable for this API.
