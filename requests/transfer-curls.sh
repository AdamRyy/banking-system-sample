#!/usr/bin/env bash
# Temporary manual test requests for POST /api/v1/transfers
# Start the app first: ./mvnw spring-boot:run
# State is in-memory and shared, so run against a freshly started app
# (balance 5000.00, transfer limit 10000.00) and in this order.

BASE_URL="http://localhost:8080/api/v1"
JSON="Content-Type: application/json"
AUTH="Authorization: Bearer token-123"
CID="X-Correlation-Id: test-correlation-id-001"

echo "=== 1. Valid transfer of 100 -> expect 201, accBalance 4900.00, remainingLimit 9900.00"
curl -i -X POST "$BASE_URL/transfers" -H "$JSON" -H "$AUTH" -H "$CID" -d '{"sourceId":1,"targetId":2,"amount":100}'
echo -e "\n"

echo "=== 2. Non-existing source account -> expect 404 ACCOUNT_NOT_FOUND"
curl -i -X POST "$BASE_URL/transfers" -H "$JSON" -H "$AUTH" -H "$CID" -d '{"sourceId":999,"targetId":2,"amount":10}'
echo -e "\n"

echo "=== 3. Transfer to the same account -> expect 400 VALIDATION_FAILED"
curl -i -X POST "$BASE_URL/transfers" -H "$JSON" -H "$AUTH" -H "$CID" -d '{"sourceId":1,"targetId":1,"amount":10}'
echo -e "\n"

echo "=== 4. Limit exceeded (remaining limit 9900.00) -> expect 422 TRANSFER_LIMIT_EXCEEDED"
curl -i -X POST "$BASE_URL/transfers" -H "$JSON" -H "$AUTH" -H "$CID" -d '{"sourceId":1,"targetId":2,"amount":9900.01}'
echo -e "\n"

echo "=== 5. Balance exceeded (6000 > 4900, within limit 9900) -> expect 422 INSUFFICIENT_FUNDS"
curl -i -X POST "$BASE_URL/transfers" -H "$JSON" -H "$AUTH" -H "$CID" -d '{"sourceId":1,"targetId":2,"amount":6000}'
echo -e "\n"

echo "=== Check: limits after the run -> expect transfer 9900.00 (only request 1 succeeded)"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/1/limits"
echo
