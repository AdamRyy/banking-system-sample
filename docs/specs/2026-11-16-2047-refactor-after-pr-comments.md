# Daily transfer limit with reset

- **Exported:** 2026-11-16 20:47
- **Status:** Approved
- **Session:** `8e612954-c7c0-47e0-9136-765e492af10b`
- **Project:** `banking-system-sample`

## Request

add new fields in limit and db schema. In schema name fields daily_limit_transfer, daily_limit_used, daily_limit_reset_at TIMESTAMP WITH TIME ZONE. Then in transfer method in @TransferService we should check if daily limit reset at is < today if so then reset it to 0 as well as update timestamp to end of current day.

## Conversation

✎ **Plan edit** — `Write` applied (wrote 76 lines)

**Plan v1 — rejected.** Feedback: remember that any actions on limit object should be made via methods exposed for outside world by Account class. also explain in detail this part:  - GET /accounts/{id}/limits will still show yesterday's used until the next transfer resets it. Fix later
   by computing the effective used on read, or exposing resetAt in LimitsResponse.

<details><summary>Plan v1</summary>

### Daily transfer limit with reset

#### Context
The transfer limit is currently a lifetime cap: `limit_used` only grows. It should be a **daily** limit: the
first transfer of a new day starts again from 0. To do that we store when the current window ends
(`daily_limit_reset_at`), and `TransferService.transfer` resets `used` when that moment has passed.

#### Changes

##### 1. `src/main/resources/schema.sql`
Rename the two limit columns and add the reset timestamp (same type as `transfer_history.executed_at`):
```sql
daily_limit_transfer DECIMAL(17, 2)              NOT NULL,
daily_limit_used     DECIMAL(17, 2)              NOT NULL,
daily_limit_reset_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
```
No DEFAULT, no CHECK (per CLAUDE.md); the entity always sets the value.

##### 2. `src/main/resources/data.sql`
Use the new column names and seed `daily_limit_reset_at` with a past value
(`TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00'`), so the first transfer opens today's window.

##### 3. `src/main/java/com/example/banking/model/Limits.java`
- Update `@Column` names: `daily_limit_transfer`, `daily_limit_used`. The "reserved word" comment goes,
  since the new names avoid `limit` anyway.
- New field `@NotNull @Column(name = "daily_limit_reset_at", nullable = false) private Instant resetAt;`
  (`Instant` maps to `TIMESTAMP WITH TIME ZONE`, as in `TransferHistory`).
- No-arg constructor sets `resetAt = Instant.EPOCH` (already expired, so the first transfer sets a real one).
- New method:
  ```java
  /** Starts a new daily window once the old one has ended. */
  void resetIfExpired(Clock clock) {
      Instant now = clock.instant();
      if (resetAt.isBefore(now)) {
          used = MoneyUtils.normalize(BigDecimal.ZERO);
          resetAt = endOfDay(now, clock.getZone());
      }
  }
  ```
  `endOfDay` = `LocalDate.ofInstant(now, zone).atTime(LocalTime.MAX).atZone(zone).toInstant()`
  truncated to microseconds (`truncatedTo(ChronoUnit.MICROS)`), because the column is `TIMESTAMP(6)` and H2
  would otherwise round 23:59:59.999999999 up to the next midnight.
- "Day" = day in the `Clock`'s zone (UTC today via `ClockConfig`); switching to e.g. `Europe/Warsaw` later is a
  one-line change in `ClockConfig`.

##### 4. `src/main/java/com/example/banking/model/Account.java`
Public delegate, since `TransferService` is in another package and `Limits` methods are package-level:
```java
public void resetDailyLimitIfExpired(Clock clock) { limits.resetIfExpired(clock); }
```

##### 5. `src/main/java/com/example/banking/service/TransferService.java`
Before `source.debit(...)`:
```java
source.resetDailyLimitIfExpired(clock);
```
Only the source: limits apply to outgoing transfers only. The reset is a change to the managed entity, so it is
written by dirty checking under the same `@Version` check (ADR 0004) — no `save()`.

##### 6. Tests
- `LimitsTest`: reset when `resetAt` is in the past (used → 0, resetAt → today 23:59:59.999999 UTC); no reset when
  still in the window (used kept); boundary just before/after the stored end of day. Use `Clock.fixed(...)`.
- `TransferServiceTest`: inject a fixed `Clock` (check how it is built now); a source whose limit was used
  yesterday can transfer again today, and `remainingLimit` in the response reflects the reset.
- `PersistenceTest`: round-trip `resetAt` through H2 (also proves the microsecond truncation keeps the day).
- Existing tests mostly keep working: `new Limits()` still compiles; assertions on `getRemaining()` hold because
  the first reset sets `used = 0`.

#### Not in scope (mention to user)
- `GET /accounts/{id}/limits` will still show yesterday's `used` until the next transfer resets it. Fix later
  by computing the effective `used` on read, or exposing `resetAt` in `LimitsResponse`.

#### Verification
1. `./mvnw test` — all green (startup with `ddl-auto=validate` confirms schema and entity match).
2. `./mvnw spring-boot:run`, then POST a transfer from account 1 and GET its limits: `used` = amount.
3. Unit test with a clock moved to the next day covers the actual reset path.

</details>

✎ **Plan edit** — `Edit` applied (+2 −1)

<details><summary>diff</summary>

````diff
--- before
+++ after
@@ -46,3 +46,4 @@
 ### 4. `src/main/java/com/example/banking/model/Account.java`
-Public delegate, since `TransferService` is in another package and `Limits` methods are package-level:
+Rule: code outside `model` changes `Limits` only through public `Account` methods. `Limits.resetIfExpired` stays
+package-private (like `allow`/`use`), and `Account` exposes the only entry point:
 ```java
````

</details>

✎ **Plan edit** — `Edit` applied (+4 −3)

<details><summary>diff</summary>

```diff
--- before
+++ after
@@ -69,5 +69,6 @@
 
-## Not in scope (mention to user)
-- `GET /accounts/{id}/limits` will still show yesterday's `used` until the next transfer resets it. Fix later
-  by computing the effective `used` on read, or exposing `resetAt` in `LimitsResponse`.
+## Known gap (not in scope)
+The reset happens only on a transfer, so `GET /accounts/{id}/limits` can show yesterday's `used` until the next
+transfer. Possible fixes: compute the effective `used` on read, or expose `resetAt`. Explained to the user in chat;
+pending their decision.
 
```

</details>

**Claude:**

I saved your rule as a memory and wrote it into the plan. The plan already worked that way: `TransferService` calls only `source.resetDailyLimitIfExpired(clock)`, and `Limits.resetIfExpired` stays package-private like `allow`/`use`.

## Why GET /limits would show stale data

**How the reset works in the plan:** the stored row changes only inside `TransferService.transfer`. Nothing else writes to it: no scheduled job and no reset when the row is read.

**Example** (UTC clock):

| Time | Event | Row in DB (`used` / `reset_at`) | `GET /limits` returns |
|---|---|---|---|
| Nov 16, 14:00 | transfer 3000 | 3000 / Nov 16 23:59:59.999999 | used 3000, remaining 7000 ✅ |
| Nov 17, 09:00 | **no transfer yet**, client checks limits | 3000 / Nov 16 23:59:59.999999 (unchanged) | used 3000, remaining 7000 ❌ should be 0 / 10000 |
| Nov 17, 09:05 | transfer 500 | reset → 0, then +500 = 500 / Nov 17 23:59:59.999999 | used 500, remaining 9500 ✅ |

Transfers always behave correctly: the reset runs before `debit`, so nobody is wrongly refused. The problem is only what clients see. `AccountService.getLimits` → `LimitsResponse.from(limits)` returns the raw columns. A client could see "remaining 7000" in the morning and not try a 9000 transfer, even though it would succeed.

**Fix options:**

1. **Compute the effective value on read (recommended).** `Limits` gets read-only methods such as `getEffectiveUsed(Clock)`, which returns 0 if `resetAt` has passed and the stored value otherwise, and `getRemaining(Clock)`. `Account` exposes them, `LimitsResponse.from` uses them, and nothing is written. The read stays a plain SELECT with no `@Version` bump, so a GET can't cause a 409 for a concurrent transfer.
2. **Reset when reading.** `getLimits` becomes `@Transactional` and calls `account.resetDailyLimitIfExpired(clock)`. Then a GET writes to the DB and bumps `version`, so a transfer running at the same time can get a 409 because of a read. It works, but having a read change data is poor practice.
3. **Return `resetAt` in `LimitsResponse`.** This doesn't fix the stale values. The client has to work out itself that "reset_at has passed, so used is really 0". It's useful extra information, but it can't be the only fix.

`✶ Insight ─────────────────────────────────────`
- Option 1 fits the "Account as aggregate root" rule well: the "is the window expired?" logic lives in one place in `Limits`, and the reset and the read both use the same `isExpired(now)` check.
- With option 1, `TransferResponse.remainingLimit` (`TransferResponse.java:16`) needs no change, because by then the transfer has already done the reset.
`─────────────────────────────────────────────────`

**Claude asked:**

- The daily reset runs only during a transfer, so GET /accounts/{id}/limits can show yesterday's 'used' until the next transfer. How should this plan handle that?
  - *Compute on read (Recommended)* — Read-only effective used/remaining via Account methods; GET writes nothing, so it causes no 409s.
  - *Compute + expose resetAt* — Same as above, and also add resetAt to LimitsResponse so clients know when the window ends.
  - *Reset when reading* — getLimits becomes @Transactional and calls resetDailyLimitIfExpired; a GET then writes and bumps the version.
  - *Leave it for later* — Keep the plan as it is; the GET shows stored values until the next transfer.

**User answered:**

- **The daily reset runs only during a transfer, so GET /accounts/{id}/limits can show yesterday's 'used' until the next transfer. How should this plan handle that?** → dont change it for now but put a reminder in account controller in comment from

✎ **Plan edit** — `Edit` applied (+7 −4)

<details><summary>diff</summary>

````diff
--- before
+++ after
@@ -69,6 +69,9 @@
 
-## Known gap (not in scope)
-The reset happens only on a transfer, so `GET /accounts/{id}/limits` can show yesterday's `used` until the next
-transfer. Possible fixes: compute the effective `used` on read, or expose `resetAt`. Explained to the user in chat;
-pending their decision.
+### 7. `src/main/java/com/example/banking/controller/AccountController.java`
+The logic stays as it is (the user's decision). Add a short reminder comment above `getLimits`:
+```java
+// TODO: the daily limit resets only on a transfer, so after midnight this can show yesterday's "used"
+//  until the next transfer. Fix: compute the effective used/remaining on read via Account methods.
+@GetMapping("/{accountId}/limits")
+```
 
````

</details>

**Plan v2 — approved** (see *Final plan* below)

<details><summary>Changes from v1</summary>

````diff
--- v1
+++ v2
@@ -46,3 +46,4 @@
 ### 4. `src/main/java/com/example/banking/model/Account.java`
-Public delegate, since `TransferService` is in another package and `Limits` methods are package-level:
+Rule: code outside `model` changes `Limits` only through public `Account` methods. `Limits.resetIfExpired` stays
+package-private (like `allow`/`use`), and `Account` exposes the only entry point:
 ```java
@@ -68,5 +69,6 @@
 
-## Not in scope (mention to user)
-- `GET /accounts/{id}/limits` will still show yesterday's `used` until the next transfer resets it. Fix later
-  by computing the effective `used` on read, or exposing `resetAt` in `LimitsResponse`.
+## Known gap (not in scope)
+The reset happens only on a transfer, so `GET /accounts/{id}/limits` can show yesterday's `used` until the next
+transfer. Possible fixes: compute the effective `used` on read, or expose `resetAt`. Explained to the user in chat;
+pending their decision.
 
````

</details>

<details><summary>User's edits on approval</summary>

````diff
--- v2 (Claude)
+++ v2 (approved)
@@ -69,6 +69,9 @@
 
-## Known gap (not in scope)
-The reset happens only on a transfer, so `GET /accounts/{id}/limits` can show yesterday's `used` until the next
-transfer. Possible fixes: compute the effective `used` on read, or expose `resetAt`. Explained to the user in chat;
-pending their decision.
+### 7. `src/main/java/com/example/banking/controller/AccountController.java`
+The logic stays as it is (the user's decision). Add a short reminder comment above `getLimits`:
+```java
+// TODO: the daily limit resets only on a transfer, so after midnight this can show yesterday's "used"
+//  until the next transfer. Fix: compute the effective used/remaining on read via Account methods.
+@GetMapping("/{accountId}/limits")
+```
 
````

</details>

## Final plan

### Context
The transfer limit is currently a lifetime cap: `limit_used` only grows. It should be a **daily** limit: the
first transfer of a new day starts again from 0. To do that we store when the current window ends
(`daily_limit_reset_at`), and `TransferService.transfer` resets `used` when that moment has passed.

### Changes

#### 1. `src/main/resources/schema.sql`
Rename the two limit columns and add the reset timestamp (same type as `transfer_history.executed_at`):
```sql
daily_limit_transfer DECIMAL(17, 2)              NOT NULL,
daily_limit_used     DECIMAL(17, 2)              NOT NULL,
daily_limit_reset_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
```
No DEFAULT, no CHECK (per CLAUDE.md); the entity always sets the value.

#### 2. `src/main/resources/data.sql`
Use the new column names and seed `daily_limit_reset_at` with a past value
(`TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00'`), so the first transfer opens today's window.

#### 3. `src/main/java/com/example/banking/model/Limits.java`
- Update `@Column` names: `daily_limit_transfer`, `daily_limit_used`. The "reserved word" comment goes,
  since the new names avoid `limit` anyway.
- New field `@NotNull @Column(name = "daily_limit_reset_at", nullable = false) private Instant resetAt;`
  (`Instant` maps to `TIMESTAMP WITH TIME ZONE`, as in `TransferHistory`).
- No-arg constructor sets `resetAt = Instant.EPOCH` (already expired, so the first transfer sets a real one).
- New method:
  ```java
  /** Starts a new daily window once the old one has ended. */
  void resetIfExpired(Clock clock) {
      Instant now = clock.instant();
      if (resetAt.isBefore(now)) {
          used = MoneyUtils.normalize(BigDecimal.ZERO);
          resetAt = endOfDay(now, clock.getZone());
      }
  }
  ```
  `endOfDay` = `LocalDate.ofInstant(now, zone).atTime(LocalTime.MAX).atZone(zone).toInstant()`
  truncated to microseconds (`truncatedTo(ChronoUnit.MICROS)`), because the column is `TIMESTAMP(6)` and H2
  would otherwise round 23:59:59.999999999 up to the next midnight.
- "Day" = day in the `Clock`'s zone (UTC today via `ClockConfig`); switching to e.g. `Europe/Warsaw` later is a
  one-line change in `ClockConfig`.

#### 4. `src/main/java/com/example/banking/model/Account.java`
Rule: code outside `model` changes `Limits` only through public `Account` methods. `Limits.resetIfExpired` stays
package-private (like `allow`/`use`), and `Account` exposes the only entry point:
```java
public void resetDailyLimitIfExpired(Clock clock) { limits.resetIfExpired(clock); }
```

#### 5. `src/main/java/com/example/banking/service/TransferService.java`
Before `source.debit(...)`:
```java
source.resetDailyLimitIfExpired(clock);
```
Only the source: limits apply to outgoing transfers only. The reset is a change to the managed entity, so it is
written by dirty checking under the same `@Version` check (ADR 0004) — no `save()`.

#### 6. Tests
- `LimitsTest`: reset when `resetAt` is in the past (used → 0, resetAt → today 23:59:59.999999 UTC); no reset when
  still in the window (used kept); boundary just before/after the stored end of day. Use `Clock.fixed(...)`.
- `TransferServiceTest`: inject a fixed `Clock` (check how it is built now); a source whose limit was used
  yesterday can transfer again today, and `remainingLimit` in the response reflects the reset.
- `PersistenceTest`: round-trip `resetAt` through H2 (also proves the microsecond truncation keeps the day).
- Existing tests mostly keep working: `new Limits()` still compiles; assertions on `getRemaining()` hold because
  the first reset sets `used = 0`.

#### 7. `src/main/java/com/example/banking/controller/AccountController.java`
The logic stays as it is (the user's decision). Add a short reminder comment above `getLimits`:
```java
// TODO: the daily limit resets only on a transfer, so after midnight this can show yesterday's "used"
//  until the next transfer. Fix: compute the effective used/remaining on read via Account methods.
@GetMapping("/{accountId}/limits")
```

### Verification
1. `./mvnw test` — all green (startup with `ddl-auto=validate` confirms schema and entity match).
2. `./mvnw spring-boot:run`, then POST a transfer from account 1 and GET its limits: `used` = amount.
3. Unit test with a clock moved to the next day covers the actual reset path.
