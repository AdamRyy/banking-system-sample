# 0009. Transfer events on Kafka, sent after commit

Status: Accepted
Date: 2026-11-20

## Context
Other services (notifications, fraud detection) must react to completed transfers without the transfer
calling them directly. They must never see a transfer that was rolled back.

## Decision
- `TransferService` publishes a Spring `TransferCompletedEvent`. `TransferEventPublisher`
  (`@TransactionalEventListener(AFTER_COMMIT)`) sends it as JSON to topic `bank-transfers` (3 partitions),
  keyed by `sourceAccountNumber`, so one account's outgoing transfers stay in order. `transferId` is the
  OUTGOING history row's id.
- The send is a direct `kafkaTemplate.send()` on the request thread. A failure is only logged: the transfer
  is already committed, so it must not reach the client.
- Each consumer has its own group (`notification-service-group`, `fraud-detection-group`), so each gets every
  event. A consumer skips (logs) a malformed record instead of throwing, because a retry can't fix it.

## Consequences
- A rollback (404, 422, 409) publishes nothing.
- The DB commit and the Kafka send aren't atomic: if the broker is down or the JVM dies after the commit, the
  event is lost.
- While the broker is down, each transfer waits up to `max.block.ms` (5 s) on the request thread and keeps its
  DB connection, so a Kafka outage can drain the connection pool and slow the whole API.
- Delivery is at least once and `transferId` restarts at 1 after every DB reset. Consumers aren't idempotent
  yet, and a new group replays the whole topic (`auto-offset-reset: earliest`).

## Next iteration
- Replace the direct send with a **transactional outbox**: write the event to an `outbox` table in the transfer's
  transaction, and let a separate relay send it to Kafka and mark it sent. That closes the lost-event gap and
  takes Kafka off the request path. It needs its own ADR, which supersedes this one.
- Add an `eventId` (UUID) to the event so consumers can deduplicate (e.g. a `processed_events` table).
