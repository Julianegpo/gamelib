# Game Library

Personal game-library app: track games across platforms, set play statuses
(wishlist, playing, beaten, …), and organize curated lists. Monorepo with two
applications mid-migration from file-based persistence to an event-driven
backend.

## Repository layout

```
gamelib/
├── webapp/    # Astro 7 frontend (SSR) — current source of truth
└── server/    # Spring Boot + Kafka backend — future source of truth
```

## Architecture & migration plan

```
Phase 0 (now)                    Phase 1 (in progress)              Phase 2 (future)
┌──────────────┐                 ┌──────────────┐    ┌───────┐     ┌──────────────┐
│   webapp/    │  mutations      │   webapp/    │───▶│ Kafka │───▶│   server/    │
│  Astro + JSON│ ──────────────▶ │  Astro + API │    │events │    │ Spring Boot  │
│  data/*.json │   file writes   │   calls      │    └───────┘    │  PostgreSQL  │
└──────────────┘                 └──────────────┘                 └──────────────┘
```

- **webapp/** — Astro 7 + Tailwind, SSR via Node adapter. All persistence goes
  through `src/lib/db.ts`, which reads/writes four JSON files in `data/`
  (games, users, library, lists). Sessions are session-based user selection
  with no passwords (deliberate scope decision).
- **server/** — Spring Boot 3.5 (Java 25), REST API + Spring Kafka + PostgreSQL
  17 with Flyway migrations. Services write to the DB and publish domain
  events (game created, library status changed, list updated); consumers
  demonstrate idempotent event handling. Seed data mirrors `webapp/data/*.json`.

The migration runs in parallel: JSON files keep working while the backend is
built out. A BFF layer (NestJS) is deliberately deferred until it's needed;
auth (session cookies first) is also deferred. See `webapp/AGENTS.md` for the
full architecture analysis and decision log.

## Domain

- **Game** — title, platform, release year, description (slug id, e.g. `hollow-knight`)
- **User** — username + display name, no passwords
- **Library entry** — a user's status for a game: `wishlist | backlog | playing | beaten | paused | quit`
- **List** — a user's named collection of games (e.g. "Mis platinos")

## Running

**Frontend:**

```bash
cd webapp
npm install
npm run dev        # http://localhost:4321
```

**Backend** (see `server/README.md` for details):

```bash
cd server
docker compose up -d          # Kafka + PostgreSQL
mvn spring-boot:run           # http://localhost:8080
mvn test                      # tests run without Docker (H2 + embedded Kafka)
```

## Status

| Area | State |
|------|-------|
| Webapp UI (games, users, lists, statuses) | Working, JSON persistence |
| Backend scaffold (REST, JPA, Flyway, Kafka) | Built, unverified (Maven/Docker not installed yet) |
| Webapp → backend wiring | Not started |
| Auth | Deferred (session cookies planned first) |
| BFF layer | Deferred until needed |
