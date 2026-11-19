# Add Apache Kafka (KRaft) to docker-compose

- **Exported:** 2026-11-19 17:16
- **Status:** Approved
- **Session:** `ba59bfeb-7bc3-4a3e-92db-686325a790a4`
- **Project:** `C:\Users\Adam\IdeaProjects\banking-system-sample`

## Request

add apache kafka to docker compose in KRaft mode. Broker should be able to start via Docker Compose.

## Conversation

✎ **Plan edit** — `Write` applied (wrote 68 lines)

**Plan v1 — rejected.** Feedback: for now dont create new adr files - later we add more functionalites and then its time to add/update adr.

<details><summary>Plan v1</summary>

### Add Apache Kafka (KRaft) to docker-compose

#### Context
Branch `feature/add-kafka`. The app will later publish events (e.g. transfers) to Kafka; this step only provides
the infrastructure: a single Kafka broker, in KRaft mode (no ZooKeeper), that starts with `docker compose up -d`
next to the existing `postgres` service. No Java/Spring changes yet (no `spring-kafka` dependency, no properties),
so `./mvnw test` / `verify` are unaffected.

#### Changes

##### 1. `docker-compose.yml` — new `kafka` service
Update the header comment ("Local development database" → "Local development infrastructure"), then add:

```yaml
  kafka:
    image: apache/kafka:4.1.0          # official Apache image; Kafka 4.x is KRaft-only
    container_name: banking-kafka
    ports:
      - "9092:9092"
    environment:
      # Single node acting as both broker and KRaft controller.
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      # The app runs on the host, so clients are told to connect to localhost:9092.
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      # Internal topics default to replication factor 3, impossible with one broker.
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
      interval: 10s
      timeout: 10s
      retries: 10
      start_period: 15s
```

Notes:
- Any `KAFKA_*` env var makes the image ignore its bundled `server.properties`, so the full set above is required.
- No volume, same as Postgres (ADR 0008): topics and messages are lost on `docker compose down`.
- No credentials → `.env` / `.env.example` unchanged. `CLUSTER_ID` left to the image default (fine for one dev node).
- Controller port 9093 is not published; only the broker port is needed on the host.

##### 2. Docs (project convention: infra decisions are ADRs)
- `docs/adr/0009-kafka-in-docker.md` (Status: Accepted, Date: 2026-11-19): single KRaft node from compose,
  `apache/kafka`, advertised `localhost:9092` because the app runs on the host, no volume, no auth;
  consequences: a container-to-container client would need a second listener; messaging integration tests
  will need a Testcontainers Kafka (later ADR/step).
- `docs/adr/README.md`: add row 0009. `CLAUDE.md`: add `@docs/adr/0009-kafka-in-docker.md` import and mention
  Kafka in the intro line about Docker.
- `README.md` (lines ~14 and ~39–44): `docker compose up -d` now starts Postgres **and** Kafka
  (`banking-kafka`, port 9092); add `docker compose logs -f kafka` and a topic-list command:
  `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`.

#### Verification
1. `docker compose config` — file is valid.
2. `docker compose up -d` then `docker compose ps` — both `banking-postgres` and `banking-kafka` show `(healthy)`.
3. `docker compose logs kafka` — contains "Kafka Server started" and KRaft mode, no ZooKeeper.
4. Smoke test inside the container:
   - `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic smoke-test`
   - produce one message with `kafka-console-producer.sh`, read it back with
     `kafka-console-consumer.sh --from-beginning --max-messages 1`, then delete the topic.
5. `./mvnw test` still passes (sanity; nothing Java changed).

</details>

✎ **Plan edit** — `Edit` applied (+3 −7)

<details><summary>diff</summary>

```diff
--- before
+++ after
@@ -48,9 +48,5 @@
 
-### 2. Docs (project convention: infra decisions are ADRs)
-- `docs/adr/0009-kafka-in-docker.md` (Status: Accepted, Date: 2026-11-19): single KRaft node from compose,
-  `apache/kafka`, advertised `localhost:9092` because the app runs on the host, no volume, no auth;
-  consequences: a container-to-container client would need a second listener; messaging integration tests
-  will need a Testcontainers Kafka (later ADR/step).
-- `docs/adr/README.md`: add row 0009. `CLAUDE.md`: add `@docs/adr/0009-kafka-in-docker.md` import and mention
-  Kafka in the intro line about Docker.
+### 2. Docs (no ADR for now: it comes once the app actually uses Kafka)
+- `CLAUDE.md`: one sentence in the intro saying `docker-compose.yml` also starts a single-node Kafka broker
+  (KRaft, `localhost:9092`) that the app doesn't use yet.
 - `README.md` (lines ~14 and ~39–44): `docker compose up -d` now starts Postgres **and** Kafka
```

</details>

**Plan v2 — approved** (see *Final plan* below)

<details><summary>Changes from v1</summary>

_(identical to the previous version)_

</details>

<details><summary>User's edits on approval</summary>

```diff
--- v2 (Claude)
+++ v2 (approved)
@@ -48,9 +48,5 @@
 
-### 2. Docs (project convention: infra decisions are ADRs)
-- `docs/adr/0009-kafka-in-docker.md` (Status: Accepted, Date: 2026-11-19): single KRaft node from compose,
-  `apache/kafka`, advertised `localhost:9092` because the app runs on the host, no volume, no auth;
-  consequences: a container-to-container client would need a second listener; messaging integration tests
-  will need a Testcontainers Kafka (later ADR/step).
-- `docs/adr/README.md`: add row 0009. `CLAUDE.md`: add `@docs/adr/0009-kafka-in-docker.md` import and mention
-  Kafka in the intro line about Docker.
+### 2. Docs (no ADR for now: it comes once the app actually uses Kafka)
+- `CLAUDE.md`: one sentence in the intro saying `docker-compose.yml` also starts a single-node Kafka broker
+  (KRaft, `localhost:9092`) that the app doesn't use yet.
 - `README.md` (lines ~14 and ~39–44): `docker compose up -d` now starts Postgres **and** Kafka
```

</details>

## Final plan

### Context
Branch `feature/add-kafka`. The app will later publish events (e.g. transfers) to Kafka; this step only provides
the infrastructure: a single Kafka broker, in KRaft mode (no ZooKeeper), that starts with `docker compose up -d`
next to the existing `postgres` service. No Java/Spring changes yet (no `spring-kafka` dependency, no properties),
so `./mvnw test` / `verify` are unaffected.

### Changes

#### 1. `docker-compose.yml` — new `kafka` service
Update the header comment ("Local development database" → "Local development infrastructure"), then add:

```yaml
  kafka:
    image: apache/kafka:4.1.0          # official Apache image; Kafka 4.x is KRaft-only
    container_name: banking-kafka
    ports:
      - "9092:9092"
    environment:
      # Single node acting as both broker and KRaft controller.
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      # The app runs on the host, so clients are told to connect to localhost:9092.
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      # Internal topics default to replication factor 3, impossible with one broker.
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
      interval: 10s
      timeout: 10s
      retries: 10
      start_period: 15s
```

Notes:
- Any `KAFKA_*` env var makes the image ignore its bundled `server.properties`, so the full set above is required.
- No volume, same as Postgres (ADR 0008): topics and messages are lost on `docker compose down`.
- No credentials → `.env` / `.env.example` unchanged. `CLUSTER_ID` left to the image default (fine for one dev node).
- Controller port 9093 is not published; only the broker port is needed on the host.

#### 2. Docs (no ADR for now: it comes once the app actually uses Kafka)
- `CLAUDE.md`: one sentence in the intro saying `docker-compose.yml` also starts a single-node Kafka broker
  (KRaft, `localhost:9092`) that the app doesn't use yet.
- `README.md` (lines ~14 and ~39–44): `docker compose up -d` now starts Postgres **and** Kafka
  (`banking-kafka`, port 9092); add `docker compose logs -f kafka` and a topic-list command:
  `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`.

### Verification
1. `docker compose config` — file is valid.
2. `docker compose up -d` then `docker compose ps` — both `banking-postgres` and `banking-kafka` show `(healthy)`.
3. `docker compose logs kafka` — contains "Kafka Server started" and KRaft mode, no ZooKeeper.
4. Smoke test inside the container:
   - `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic smoke-test`
   - produce one message with `kafka-console-producer.sh`, read it back with
     `kafka-console-consumer.sh --from-beginning --max-messages 1`, then delete the topic.
5. `./mvnw test` still passes (sanity; nothing Java changed).
