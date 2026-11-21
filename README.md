# banking-system-sample

Spring Boot 4.1 sample banking REST API. The database is PostgreSQL running in Docker; the app itself runs on the host.

## Prerequisites

- JDK 25
- Docker Desktop (running), with Docker Compose v2

## Run locally

```bash
cp .env.example .env          # once: database credentials (git-ignored), edit the values
docker compose up -d          # start PostgreSQL (banking-postgres, port 5432) and Kafka (banking-kafka, port 9092)
docker compose ps             # wait until STATUS shows (healthy)
./mvnw spring-boot:run        # start the app on http://localhost:8080
```

The app drops and recreates the tables from `schema.sql` + `data.sql` on every start, so each start begins with
the two seeded test accounts (ids 1 and 2). Data does not survive an app restart.

`POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` live in `.env`. Docker Compose reads the file to create the
database, and the app imports it (`spring.config.import` in `application.yml`), so both use the same login.
`.env` is git-ignored, and `.env.example` is the committed template. Environment variables with the same names
override the file. If you change the credentials after the container exists, run `docker compose down` and then
`docker compose up -d`, because Postgres only applies them when it creates the container.

Example request:

```bash
curl -X POST localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer token-123" -H "X-Correlation-Id: demo-1" -H "Content-Type: application/json" \
  -d '{"sourceId":1,"targetId":2,"amount":250.00}'
```

## Docker commands

```bash
docker compose up -d                                       # start PostgreSQL, Kafka and Kafka UI in the background
docker compose ps                                          # status and health
docker compose logs -f postgres                            # follow the database logs
docker compose logs -f kafka                               # follow the Kafka broker logs
docker compose exec postgres psql -U banking -d banking    # SQL shell (\dt lists tables, \q quits)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list   # list topics
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic bank-transfers
docker compose stop                                        # stop the containers, keep them
docker compose down                                        # stop and remove the containers
```

Kafka is a single node in KRaft mode (no ZooKeeper) and has no volume, so topics and messages are lost on
`docker compose down`. The app needs it at startup (it fails fast without a broker) and creates the topic
`bank-transfers` (3 partitions, replication factor 1) if it's missing.

## Kafka UI (browse events in real time)

`docker compose up -d` also starts [Kafka UI](https://github.com/kafbat/kafka-ui) (container `banking-kafka-ui`).

1. `docker compose up -d`, then start the app (`./mvnw spring-boot:run`) so it creates `bank-transfers`.
2. Open http://localhost:8081 (port 8080 is the app) and pick the cluster **local**.
3. Go to **Topics → bank-transfers → Messages**.
4. Set the mode dropdown to **Live mode** to watch events arrive as they're produced. Use **Oldest** to see everything
   already in the topic. Click a row to see its value, key, headers, partition and offset.
5. Optional: filter with the search box, or pick one partition.

Every successful transfer publishes a `TransferCompletedEvent` as JSON, keyed by the source account number, once the
database transaction has committed. A rejected transfer (404, 422, 409) publishes nothing. With Live mode open, send
the example request from above and the event appears:

```json
{"transferId":1,"sourceAccountNumber":"12345678901234567890123456","targetAccountNumber":"65432109876543210987654321","amount":250.00,"timestamp":"2026-11-19T17:00:00Z"}
```

On Git Bash for Windows, prefix `docker compose exec` commands with `MSYS_NO_PATHCONV=1`, otherwise `/opt/...` is
rewritten to a Windows path.

## Tests

```bash
./mvnw test      # unit tests only, no Docker needed
./mvnw verify    # unit + integration tests
```

Integration tests start their own throwaway PostgreSQL with Testcontainers, so Docker must be running, but
`docker compose up` is not needed and the dev database is never touched.
