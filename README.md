# banking-system-sample

Spring Boot 4.1 sample banking REST API. The database is PostgreSQL running in Docker; the app itself runs on the host.

## Prerequisites

- JDK 25
- Docker Desktop (running), with Docker Compose v2

## Run locally

```bash
cp .env.example .env          # once: database credentials (git-ignored), edit the values
docker compose up -d          # start PostgreSQL (container banking-postgres, port 5432)
docker compose ps             # wait until STATUS shows (healthy)
./mvnw spring-boot:run        # start the app on http://localhost:8080
```

The app drops and recreates the tables from `schema.sql` + `data.sql` on every start, so each start begins with
the two seeded test accounts (ids 1 and 2). Data does not survive an app restart.

`POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` live in `.env`. Docker Compose reads the file to create the
database, and the app imports it (`spring.config.import` in `application.properties`), so both use the same login.
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
docker compose up -d                                       # start PostgreSQL in the background
docker compose ps                                          # status and health
docker compose logs -f postgres                            # follow the database logs
docker compose exec postgres psql -U banking -d banking    # SQL shell (\dt lists tables, \q quits)
docker compose stop                                        # stop the container, keep it
docker compose down                                        # stop and remove the container
```

## Tests

```bash
./mvnw test      # unit tests only, no Docker needed
./mvnw verify    # unit + integration tests
```

Integration tests start their own throwaway PostgreSQL with Testcontainers, so Docker must be running, but
`docker compose up` is not needed and the dev database is never touched.
