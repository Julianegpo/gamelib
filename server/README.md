# Game Library Server

Event-driven backend for the game library app, built with **Spring Boot 3.5 + Kafka + PostgreSQL**.
Replaces the Astro webapp's JSON file persistence (`webapp/data/*.json`) with a real database
and an event-driven architecture.

## Stack

| Concern | Choice |
|---------|--------|
| Language | Java 25 (Corretto) |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Actuator) |
| Messaging | Spring Kafka (single-node KRaft broker in Docker) |
| Database | PostgreSQL 17, schema managed by Flyway |
| Tests | JUnit 5, Mockito, Embedded Kafka, H2 (PostgreSQL mode — no Docker needed) |

## Architecture

```
Astro frontend ──HTTP──▶ REST controllers ──▶ Services ──▶ PostgreSQL
                                                     │
                                                     └──▶ Kafka topics ──▶ Consumers
```

- **Controllers** (`api/`) — thin HTTP layer, DTOs in/out, validation
- **Services** (`service/`) — business logic + transactional writes; publish events
  after saving state
- **Domain** (`domain/`) — JPA entities mirroring the old JSON files
- **Kafka** (`kafka/`) — `EventProducer` publishes sealed-interface events;
  `EventConsumer` shows idempotent listener patterns
- **Error handling** (`error/`) — `@RestControllerAdvice` mapping domain exceptions
  to consistent `ApiError` JSON

### Topics

| Topic | Key | Event |
|-------|-----|-------|
| `game-library.games.created` | gameId | `GameCreatedEvent` |
| `game-library.library.status-changed` | username (partition affinity per user) | `LibraryStatusChangedEvent` |
| `game-library.lists.updated` | username | `ListUpdatedEvent` |

## Prerequisites

- JDK 25 (`java -version`)
- Maven (`mvn -v`) — e.g. `winget install Apache.Maven` or `choco install maven`
- Docker Desktop — only needed for *running* the app locally (dev broker + database);
  the test suite runs without it

## Running

```bash
# 1. Start Kafka + PostgreSQL
docker compose up -d

# 2. Run the server (Flyway migrates + seeds the DB on first start)
mvn spring-boot:run
```

The API is at `http://localhost:8080`, health at `/actuator/health`.

### Try it

```bash
curl http://localhost:8080/api/games
curl -X PUT http://localhost:8080/api/library/ash/games/chrono-trigger/status/PLAYING
curl http://localhost:8080/api/library/ash
curl -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -d '{"title": "Celeste", "platform": "PC", "releaseYear": 2018, "description": "Indie platformer"}'
```

Watch the consumer logs in the server console — every mutation logs an event line
from `EventConsumer`.

## Testing

```bash
mvn test          # runs without Docker: H2 (PostgreSQL mode) + Embedded Kafka
```

- `GameServiceTest` — pure Mockito, no infra needed
- `GameLibraryIntegrationTest` — full REST round-trip against H2 using the same
  Flyway migrations as Postgres, with an embedded Kafka broker verifying event
  emission/consumption

The `test` profile (`src/test/resources/application-test.yml`) points the app at
an in-memory H2 database — production always runs PostgreSQL.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/games` | List games |
| GET | `/api/games/{id}` | Get game (id = slug, e.g. `hollow-knight`) |
| POST | `/api/games` | Create game (id derived from title slug) |
| PUT | `/api/games/{id}` | Update game |
| DELETE | `/api/games/{id}` | Delete game |
| GET | `/api/users` | List users |
| GET | `/api/users/{username}` | Get user |
| POST | `/api/users` | Create user |
| GET | `/api/library/{username}` | A user's library |
| PUT | `/api/library/{username}/games/{gameId}/status/{status}` | Set status (WISHLIST, BACKLOG, PLAYING, BEATEN, PAUSED, QUIT) |
| GET | `/api/lists/user/{username}` | A user's lists |
| GET | `/api/lists/{id}` | Get one list |
| POST | `/api/lists/user/{username}` | Create list |

## Known trade-offs (intentional for MVP)

1. **Dual write** — services write to Postgres *and* publish to Kafka. If the
   broker is down, the DB write succeeds but the event is lost (logged as error).
   The fix is the **transactional outbox pattern** (write events to an `outbox`
   table in the same transaction, relay them to Kafka asynchronously) — a good
   next exercise.
2. **No auth** — mirrors the webapp's session-free design. Add Spring Security
   with session cookies when needed.
3. **Fire-and-forget publishing** happens inside the transaction but completes
   async; consumers must stay idempotent (at-least-once delivery).
