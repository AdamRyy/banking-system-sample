# 0003. Money as BigDecimal with scale 2

Status: Accepted
Date: 2026-11-13

## Context
Floating-point types can't represent amounts exactly.

## Decision
Money is `BigDecimal` with 2 decimal places, normalized via `MoneyUtils.normalize`.

## Consequences
Compare amounts with `compareTo`, not `equals`: `equals` also compares scale, so `1.0` ≠ `1.00`.
