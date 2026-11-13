-- Test accounts loaded on every start. No explicit ids: the identity column assigns 1 and 2,
-- so later JPA inserts don't collide with them.
INSERT INTO account (account_number, balance, transfer_limit, limit_used)
VALUES ('12345678901234567890123456', 5000.00, 10000.00, 0.00);
INSERT INTO account (account_number, balance, transfer_limit, limit_used)
VALUES ('65432109876543210987654321', 1000.00, 10000.00, 0.00);
