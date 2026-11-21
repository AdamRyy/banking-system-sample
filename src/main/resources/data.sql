-- Test accounts loaded on every start. No explicit ids: the identity column assigns 1 and 2,
-- so later JPA inserts don't collide with them.
-- Account 1's limit is above the fraud threshold (10 000), so a FRAUD ALERT can be triggered through the API.
-- daily_limit_reset_at is in the past, so the first transfer starts today's limit window.
INSERT INTO accounts (account_number, balance, daily_limit_transfer, daily_limit_used, daily_limit_reset_at)
VALUES ('12345678901234567890123456', 50000.00, 20000.00, 0.00, TIMESTAMP WITH TIME ZONE '2026-11-11 00:00:00+00');
INSERT INTO accounts (account_number, balance, daily_limit_transfer, daily_limit_used, daily_limit_reset_at)
VALUES ('65432109876543210987654321', 1000.00, 10000.00, 0.00, TIMESTAMP WITH TIME ZONE '2026-11-11 00:00:00+00');
