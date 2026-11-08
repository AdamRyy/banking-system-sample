#!/usr/bin/env bash
# Temporary manual test requests for GET /api/v1/accounts/{accountId}/limits
# Start the app first: ./mvnw spring-boot:run
# Run all: bash requests/limits-curls.sh   (or copy a single line into a terminal)

BASE_URL="http://localhost:8080/api/v1"
AUTH="Authorization: Bearer token-123"
CID="X-Correlation-Id: test-correlation-id-001"

echo "=== 1. Bad input (accountId = 0, violates @Positive) -> expect 400"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/0/limits"
echo -e "\n"

# Other bad inputs worth trying:
#   curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/-5/limits"    # negative -> 400 (@Positive)
#   curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/abc/limits"   # not a number -> 400 (type mismatch)

echo "=== 2. Non-existing account (accountId = 999) -> expect 404"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/999/limits"
echo -e "\n"

echo "=== 3. Existing account (accountId = 1) -> expect 200 {\"transfer\":10000.00}"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/1/limits"
echo
