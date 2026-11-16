# 0004. Transfers: one transaction with optimistic locking

Status: Accepted
Date: 2026-11-13

## Context
A transfer changes two balances. Both must change or neither, and concurrent transfers must not overwrite
each other's changes.

## Decision
`TransferService.transfer` is one `@Transactional` unit:
1. Load the source, then the target (404 if either is missing).
2. Debit the source (422 if the limit or the funds are too low), then credit the target.
3. Write two `TransferHistory` rows via `TransferHistoryRepository.saveAll`: OUTGOING on the source,
   INCOMING on the target.

There's no `save()` for the accounts: JPA dirty checking writes both on commit. `Account` has `@Version`,
so a concurrent change fails the commit with `OptimisticLockingFailureException` → 409 `CONCURRENT_MODIFICATION`.

## Consequences
- A 409 means nothing was committed: both balances and both history rows roll back together. The server
  doesn't retry, so the client may simply resend the transfer.
- Retries are only safe when the client got a response. There's no idempotency key, so resending after a
  timeout (outcome unknown) can execute the transfer twice.
- Accounts must be loaded and changed inside the transaction. `AccountService.getAccount` joins the caller's
  transaction instead of starting its own. Outside one, the changes are never flushed. Adding `save()` changes
  nothing for an entity that's already managed.
- History rows have no `@Version`. That's enough, because they're only inserted, in the same transaction as
  the version-checked balance updates.
- No row locks are held while the transfer runs, so the order of loading (source, then target) can't cause
  deadlocks; it only decides which 404 is reported first. The price is that heavy contention on one account
  shows up as many 409s instead of waiting.
