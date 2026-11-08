#!/usr/bin/env bash
# Temporary manual test requests for the required Authorization and X-Correlation-Id headers.
# Start the app first: ./mvnw spring-boot:run
# Run all: bash requests/auth-curls.sh   (or copy a single line into a terminal)

BASE_URL="http://localhost:8080/api/v1"
AUTH="Authorization: Bearer token-123"
CID="X-Correlation-Id: test-correlation-id-001"

echo "=== 1. Both headers valid -> expect 200, X-Correlation-Id echoed in the response header"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 2. No Authorization -> expect 401 UNAUTHORIZED, WWW-Authenticate: Bearer, correlation id in body"
curl -i -H "$CID" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 3. Wrong token -> expect 401 UNAUTHORIZED"
curl -i -H "Authorization: Bearer wrong-token" -H "$CID" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 4. Wrong scheme (Basic) -> expect 401 UNAUTHORIZED"
curl -i -H "Authorization: Basic token-123" -H "$CID" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 5. No X-Correlation-Id -> expect 400 MISSING_CORRELATION_ID, no correlation id in body"
curl -i -H "$AUTH" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 6. Blank X-Correlation-Id -> expect 400 MISSING_CORRELATION_ID"
curl -i -H "$AUTH" -H "X-Correlation-Id;" "$BASE_URL/accounts/1/limits"
echo -e "\n"

echo "=== 7. POST without any headers -> expect an error in ErrorResponse JSON (401 or 400, depending on check order)"
curl -i -X POST -H "Content-Type: application/json" -d '{"sourceId":1,"targetId":2,"amount":10}' "$BASE_URL/transfers"
echo -e "\n"

echo "=== 8. Unknown URL -> expect 404 NOT_FOUND as ErrorResponse JSON, not a default error page"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/nope"
echo -e "\n"

echo "=== 9. Rejected by Tomcat (encoded slash) -> expect 400 as ErrorResponse JSON, written by JsonErrorReportValve"
curl -i -H "$AUTH" -H "$CID" "$BASE_URL/accounts/%2F/limits"
echo
