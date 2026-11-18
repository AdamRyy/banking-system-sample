# GET /api/v1/accounts/{accountId}/transfers: paginated transfer history

- **Exported:** 2026-11-18 20:36
- **Status:** Approved
- **Session:** `870ed516-0ef0-4ce4-8d86-f7541011bf58`
- **Project:** `C:\Users\Adam\IdeaProjects\banking-system-sample`

## Request

What about n+1 problem and potential errors on joining tables? If im correct query to get tranbsfers related to specified accountId should be relatable safe it would just result in some query with conditional accountnumber=accountNumber related to that account?

## Conversation

**Claude:**

You're mostly right. One correction: the filter is on the foreign key `account_id`, not `account_number`. Here is what actually runs and where the risks are.

**What runs for one request**
```sql
select count(*) from accounts where id=?                         -- existsById
select th.id, th.account_id, th.amount, ... from transfer_history th
  where th.account_id=? order by th.executed_at desc, th.id desc offset ? rows fetch first ? rows only
select count(th.id) from transfer_history th where th.account_id=?   -- only when it's needed for totalPages
```
Spring Data turns `findByAccountId` into `th.account.id = ?`. Hibernate knows `account.id` is the FK column itself, so it compares the column directly and doesn't join `accounts`. You get the "query with a condition" you describe, just on the id.

If we filtered by account number instead (`findByAccountAccountNumber`), Hibernate would have to join `accounts`. That's another reason to filter by id.

**Why there's no N+1**
- `TransferHistory.account` is `LAZY`, so each loaded row only gets a proxy holding the FK value, and no query runs for it. It matters that the mapping says `LAZY` explicitly: the JPA default for `@ManyToOne` is `EAGER`, which would load the account.
- `TransferHistoryResponse.from` reads only the row's own columns and never calls `getAccount()`, so the proxy is never initialized.
- Even if someone later added `history.getAccount().getAccountNumber()` to the DTO, it would cost 1 extra query, not N. Every row on the page has the same `account_id`, and the persistence context gives all of them the same proxy instance.
- The mapping runs inside the service's `@Transactional(readOnly = true)` method. With `open-in-view=false`, touching a proxy in the controller would throw `LazyInitializationException`. Inside the service it can't.

**Where joins could break things, and how the plan prevents it**
1. **Fetch-joining a collection together with paging.** Hibernate can't put LIMIT in the SQL, so it loads every row and pages in memory (warning HHH90003004). We have no collection and no fetch join, so LIMIT/OFFSET stays in the SQL.
2. **Sorting through a relation**, e.g. `sort=account.balance`. Spring Data would add a join to `accounts`. The sort whitelist rejects that with 400 before any query runs.

`✶ Insight ─────────────────────────────────────`
- The count query isn't an N+1 either. It's one extra statement per page. Spring Data skips it when the first page has fewer rows than `size`, because it can compute the total itself.
- Comparing `account.id` doesn't touch the `accounts` table. That's why `findByAccountId` is safe, while any other property of `account` would need a join.
- The project already uses Hibernate statistics (`generate_statistics=true`, and before part 1 a query-count test). We can use the same method to keep this endpoint free of N+1.
`─────────────────────────────────────────────────`

I'm adding a test like that to the plan, so the query count is checked by a test and not only by this explanation:

✎ **Plan edit** — `Edit` applied (+10 −0)

<details><summary>diff</summary>

```diff
--- before
+++ after
@@ -82,2 +82,12 @@
   `totalElements=2` and `totalPages=2`. Account 2 shows its INCOMING rows. `/accounts/99/transfers` → 404.
+  - **N+1 check:** after `flush()` + `clear()` + `Statistics.clear()`, call `GET /accounts/1/transfers?size=1`
+    and assert `getPrepareStatementCount() == 3`: `existsById`, the page select and the count. The number doesn't
+    depend on the row count. If someone touches `history.getAccount()` in the mapping, or the relation turns
+    EAGER, the count goes up and the test fails.
+
+## Query safety (no N+1, no joins)
+- `findByAccountId` → `where th.account_id = ?`. Hibernate compares the FK column directly and doesn't join `accounts`.
+- `TransferHistory.account` stays `LAZY`, and the DTO never calls `getAccount()`. A proxy holding only the id is never loaded.
+- No collection fetch join, so LIMIT/OFFSET stays in the SQL (no in-memory paging, HHH90003004).
+- The sort whitelist blocks `account.*` paths, which would add a join.
 
```

</details>

**Plan v1 — approved** (see *Final plan* below)

## Final plan

### Context
In part 1 we removed `GET /api/v1/accounts` and the `Account.transfers` collection. Clients still need an
account's transfer history, so we add it back with paging and per-account access:
`GET /api/v1/accounts/15/transfers?page=0&size=10&sort=timestamp,desc` returns a page of DTOs.
The query uses the owning `@ManyToOne` (`TransferHistory.account`). No collection is loaded.

### API contract
- Path variable: `accountId` must be `@NotNull @Positive`, as in `/limits` (0 or a negative id → 400 `VALIDATION_FAILED`, `abc` → 400 `INVALID_PARAMETER`).
- Unknown account → 404 `ACCOUNT_NOT_FOUND`, the same as `/limits`. An empty page would hide typos in the id.
- Query parameters: Spring Data `Pageable` (`page`, `size`, `sort=prop,dir`).
  - Defaults (`@PageableDefault`): `page=0`, `size=20`, `sort=timestamp,desc` (newest first).
  - Max size 100 (`spring.data.web.pageable.max-page-size=100`). Spring caps larger values and doesn't reject them.
  - Sortable properties: only `timestamp`, `amount`, `direction`, `counterpartyAccountNumber` and `id`, the DTO's fields.
    Anything else (e.g. `account.balance`, `foo`) → 400 `INVALID_PARAMETER`.
    Without this check, Spring Data throws `PropertyReferenceException`, which the fallback turns into a 500.
    Nested paths like `account.x` would also add joins and expose columns that aren't in the DTO.
  - The service always adds `id` as a tie-breaker in the direction of the first sort order, so rows with the same
    timestamp keep the same order from page to page.
- Response (200):
  ```json
  {
    "content": [
      {"id": 7, "direction": "OUTGOING", "counterpartyAccountNumber": "6543…", "amount": 100.00, "timestamp": "2026-11-18T10:15:30Z"}
    ],
    "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
  }
  ```
  We use our own DTO and don't serialize `PageImpl`. Spring Data warns that `PageImpl`'s JSON form isn't stable.

### Production changes
1. **`repository/TransferHistoryRepository.java`**: add `Page<TransferHistory> findByAccountId(Long accountId, Pageable pageable);`.
   Spring Data resolves `AccountId` to `account.id`. Hibernate filters on the FK column without joining `accounts`
   and derives the count query.
2. **`dto/TransferHistoryResponse.java`** (new): `record(Long id, TransferDirection direction, String counterpartyAccountNumber,
   BigDecimal amount, Instant timestamp)` + `static from(TransferHistory)`. It reads only the history row's own columns,
   never the lazy `account` (memory: no lazy loading in DTOs).
3. **`dto/PageResponse.java`** (new, generic): `record PageResponse<T>(List<T> content, int page, int size, long totalElements,
   int totalPages)` + `static <T> PageResponse<T> from(Page<T> page)`.
4. **`exception/InvalidSortException.java`** (new, `RuntimeException`) + a handler in `GlobalExceptionHandler` →
   `ErrorCode.INVALID_PARAMETER`, message `sort: unknown property 'foo' (allowed: amount, counterpartyAccountNumber, direction, id, timestamp)`.
   This is the way ADR 0002 adds an error. We reuse the existing code, so `ErrorCode` doesn't change.
5. **`service/TransferHistoryService.java`** (new; `AccountService` stays about accounts):
   - Dependencies: `AccountRepository` and `TransferHistoryRepository`.
   - `@Transactional(readOnly = true) PageResponse<TransferHistoryResponse> getTransferHistory(Long accountId, Pageable pageable)`:
     1. `accountRepository.existsById(accountId)`, otherwise throw `AccountNotFoundException`. A cheap check that loads no entity.
     2. Validate the sort against `ALLOWED_SORT = Set.of(...)`, otherwise throw `InvalidSortException`.
     3. Build `PageRequest.of(page, size, sort.and(Sort.by(firstDirection, "id")))`, unless `id` is already in the sort.
     4. `historyRepository.findByAccountId(...).map(TransferHistoryResponse::from)`, then `PageResponse.from(...)`.
6. **`controller/AccountController.java`**: add
   ```java
   @GetMapping("/{accountId}/transfers")
   public PageResponse<TransferHistoryResponse> getTransfers(@PathVariable @NotNull @Positive Long accountId,
           @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable)
   ```
   It delegates to `TransferHistoryService`.
7. **`application.properties`**: `spring.data.web.pageable.max-page-size=100`, with a one-line comment.
8. **`schema.sql`**: replace `idx_transfer_history_account_id` with
   `idx_transfer_history_account_id_executed_at ON transfer_history (account_id, executed_at)`. The index serves
   both the filter and the default sort. Its leading column still covers plain `account_id` lookups.

### Docs
- **New ADR `docs/adr/0007-paginated-list-endpoints.md`**: the response shape (`PageResponse`), defaults, max size,
  the sort whitelist with an `id` tie-breaker, and 404 for an unknown parent. Add it to `docs/adr/README.md` and
  import it in `CLAUDE.md`, as the README requires.

### Tests
- **`AccountControllerTest`** (`@WebMvcTest`, mocked `TransferHistoryService`):
  - returns the JSON page (content fields, `page`/`size`/`totalElements`/`totalPages`);
  - a captured `Pageable` reflects `page=1&size=5&sort=amount,asc`;
  - no params → defaults `0 / 20 / timestamp DESC`;
  - `size=1000` → captured size 100;
  - the service throws `AccountNotFoundException` → 404, `InvalidSortException` → 400 `INVALID_PARAMETER`;
  - `accountId=0` → 400 and the service is never called.
- **`TransferHistoryServiceTest`** (Mockito): unknown account → throws, and the history repository is never called;
  disallowed sort → throws; the `id` tie-breaker is added (captured `Pageable`); the page maps to DTOs and keeps its totals.
- **`TransferHistoryIntegrationTest`** (`@DataJpaTest`): `findByAccountId` returns only that account's rows,
  in the requested order, with a correct `totalElements` across 2 pages.
- **`TransferIntegrationTest`** (full stack): after transfers 1→2 (100) and 1→2 (50),
  `GET /api/v1/accounts/1/transfers?size=1&sort=amount,desc` returns 1 item (amount 100, OUTGOING),
  `totalElements=2` and `totalPages=2`. Account 2 shows its INCOMING rows. `/accounts/99/transfers` → 404.
  - **N+1 check:** after `flush()` + `clear()` + `Statistics.clear()`, call `GET /accounts/1/transfers?size=1`
    and assert `getPrepareStatementCount() == 3`: `existsById`, the page select and the count. The number doesn't
    depend on the row count. If someone touches `history.getAccount()` in the mapping, or the relation turns
    EAGER, the count goes up and the test fails.

### Query safety (no N+1, no joins)
- `findByAccountId` → `where th.account_id = ?`. Hibernate compares the FK column directly and doesn't join `accounts`.
- `TransferHistory.account` stays `LAZY`, and the DTO never calls `getAccount()`. A proxy holding only the id is never loaded.
- No collection fetch join, so LIMIT/OFFSET stays in the SQL (no in-memory paging, HHH90003004).
- The sort whitelist blocks `account.*` paths, which would add a join.

**To check during implementation:** in Boot 4's split modules, confirm that `@WebMvcTest` still includes the
Spring Data web auto-config. Without it there's no `Pageable` resolver and no max-size property in the slice.
If it's missing, add the auto-config with `@ImportAutoConfiguration` in `AccountControllerTest`.

### Verification
1. `./mvnw verify`: all unit and integration tests pass. `ddl-auto=validate` boots with the new index.
2. Manual: `./mvnw spring-boot:run`, then make a transfer and call
   `curl -H "Authorization: Bearer token-123" -H "X-Correlation-Id: t1" "localhost:8080/api/v1/accounts/1/transfers?page=0&size=10&sort=timestamp,desc"`.
   Expect one OUTGOING row. In the SQL log, the select has `where account_id=?` and no join to `accounts`,
   followed by a count query.
