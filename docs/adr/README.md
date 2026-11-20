# Architecture Decision Records

| ADR | Decision |
|---|---|
| [0001](0001-required-request-headers.md) | Required request headers |
| [0002](0002-uniform-error-responses.md) | Uniform error responses |
| [0003](0003-money-as-bigdecimal.md) | Money as BigDecimal with scale 2 |
| [0004](0004-transfers-transaction-and-optimistic-locking.md) | Transfers: one transaction with optimistic locking |
| [0005](0005-test-strategy.md) | Test strategy |
| [0006](0006-separate-integration-test-source-set.md) | Separate source set for integration tests |
| [0007](0007-paginated-list-endpoints.md) | Paginated list endpoints |
| [0008](0008-postgresql-in-docker.md) | PostgreSQL in Docker, Testcontainers for integration tests |
| [0009](0009-transfer-events-on-kafka.md) | Transfer events on Kafka, sent after commit |

To add one, copy the format into `NNNN-short-title.md` using the next number, add it here, and import it
in `CLAUDE.md`. Don't rewrite an accepted ADR to change a decision: add a new ADR, set the old one's
status to `Superseded by NNNN`, and remove its import from `CLAUDE.md` (only accepted ADRs are imported).
